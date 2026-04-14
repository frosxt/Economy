package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.banknote.WithdrawNoteIssueResult;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRequest;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.CommandDescriptor;
import com.github.frosxt.prisoncore.command.api.CommandResult;
import com.github.frosxt.prisoncore.command.api.PermissionPolicy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

public final class WithdrawSubcommand {
    private final EconomyService economy;
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final String permissionRoot;

    public WithdrawSubcommand(final EconomyService economy, final CurrencyDefinition definition, final EconomyMessageDispatcher messages, final String permissionRoot) {
        this.economy = economy;
        this.definition = definition;
        this.messages = messages;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "withdraw")
                .permission(PermissionPolicy.playerOnly(permissionRoot + ".withdraw"))
                .description("Withdraw your balance into a physical note")
                .executor(ctx -> {
                    final CommandSender sender = (CommandSender) ctx.sender();
                    if (!(sender instanceof final Player player)) {
                        definition.messages().find("player-only").ifPresent(msg -> messages.send(sender, msg, Map.of()));
                        return new CommandResult.Success(null);
                    }

                    if (ctx.argCount() < 1) {
                        return new CommandResult.Usage("/" + ctx.label() + " withdraw <amount>");
                    }

                    final BigDecimal amount = parseAmount(ctx.arg(0));
                    if (amount == null) {
                        definition.messages().find("invalid-number").ifPresent(msg -> messages.send(
                                player,
                                msg,
                                Map.of("input", ctx.arg(0))
                        ));
                        return new CommandResult.Success(null);
                    }

                    final WithdrawNoteIssueResult result = economy.issueWithdrawNote(new WithdrawNoteRequest(
                            player.getUniqueId(),
                            definition.key(),
                            amount
                    ));
                    handleResult(player, amount, result);
                    return new CommandResult.Success(null);
                })
                .build();
    }

    private void handleResult(final Player player, final BigDecimal amount, final WithdrawNoteIssueResult result) {
        final String formatted = BalanceSubcommand.formatAmount(amount, definition);
        if (result instanceof final WithdrawNoteIssueResult.Success success) {
            final ItemStack note = success.note();
            final Map<Integer, ItemStack> overflow = player.getInventory().addItem(note);
            for (final ItemStack leftover : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
            definition.messages().find("withdraw-success").ifPresent(msg -> messages.send(
                    player,
                    msg,
                    Map.of("amount", formatted)
            ));
            return;
        }
        if (result instanceof WithdrawNoteIssueResult.WithdrawDisabled) {
            definition.messages().find("withdraw-disabled").ifPresent(msg -> messages.send(player, msg, Map.of()));
            return;
        }
        if (result instanceof WithdrawNoteIssueResult.InsufficientFunds) {
            definition.messages().find("not-enough").ifPresent(msg -> messages.send(
                    player,
                    msg,
                    Map.of("amount", formatted)
            ));
            return;
        }
        if (result instanceof final WithdrawNoteIssueResult.InvalidAmount invalid) {
            definition.messages().find("invalid-number").ifPresent(msg -> messages.send(
                    player,
                    msg,
                    Map.of("input", invalid.reason())
            ));
            return;
        }
        if (result instanceof final WithdrawNoteIssueResult.Failed failed) {
            final Map<String, String> replacements = new HashMap<>();
            replacements.put("reason", failed.reason());
            definition.messages().find("withdraw-failed").ifPresent(msg -> messages.send(player, msg, replacements));
            return;
        }
        definition.messages().find("withdraw-failed").ifPresent(msg -> messages.send(player, msg, Map.of("reason", "unknown")));
    }

    private static BigDecimal parseAmount(final String input) {
        if (input == null) {
            return null;
        }
        try {
            return new BigDecimal(input);
        } catch (final NumberFormatException e) {
            return null;
        }
    }
}
