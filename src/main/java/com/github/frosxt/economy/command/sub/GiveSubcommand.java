package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.transaction.TransactionRequest;
import com.github.frosxt.economy.api.transaction.TransactionResult;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.CommandDescriptor;
import com.github.frosxt.prisoncore.command.api.CommandResult;
import com.github.frosxt.prisoncore.command.api.PermissionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class GiveSubcommand {
    private final EconomyService economy;
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final String permissionRoot;

    public GiveSubcommand(final EconomyService economy, final CurrencyDefinition definition, final EconomyMessageDispatcher messages, final String permissionRoot) {
        this.economy = economy;
        this.definition = definition;
        this.messages = messages;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "give")
                .permission(PermissionPolicy.of(permissionRoot + ".admin.give"))
                .description("Give currency to a player")
                .completionProvider((ctx, argIndex) -> {
                    if (argIndex == 0) {
                        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    }
                    return List.of();
                })
                .executor(ctx -> {
                    final CommandSender sender = (CommandSender) ctx.sender();
                    if (ctx.argCount() < 2) {
                        return new CommandResult.Usage("/" + ctx.label() + " give <player> <amount>");
                    }
                    final String targetName = ctx.arg(0);
                    final BigDecimal amount = parseAmount(ctx.arg(1));
                    if (amount == null) {
                        definition.messages().find("invalid-number").ifPresent(msg -> messages.send(
                                sender,
                                msg,
                                Map.of("input", ctx.arg(1))
                        ));
                        return new CommandResult.Success(null);
                    }
                    final OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                    if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(targetName) == null) {
                        definition.messages().find("invalid-player").ifPresent(msg -> messages.send(
                                sender,
                                msg,
                                Map.of("player", targetName)
                        ));
                        return new CommandResult.Success(null);
                    }
                    final TransactionResult result = economy.deposit(new TransactionRequest(
                            target.getUniqueId(),
                            definition.key(),
                            amount,
                            "admin-give"
                    ));
                    handleResult(sender, targetName, amount, result);
                    return new CommandResult.Success(null);
                })
                .build();
    }

    private void handleResult(final CommandSender sender, final String targetName, final BigDecimal amount, final TransactionResult result) {
        final String formatted = BalanceSubcommand.formatAmount(amount, definition);
        if (result instanceof TransactionResult.Success) {
            definition.messages().find("given-sender").ifPresent(msg -> messages.send(
                    sender,
                    msg,
                    Map.of("player", targetName, "amount", formatted)
            ));
            final Player online = Bukkit.getPlayerExact(targetName);
            if (online != null) {
                definition.messages().find("given-receiver").ifPresent(msg -> messages.send(
                        online,
                        msg,
                        Map.of("amount", formatted)
                ));
            }
            return;
        }
        if (result instanceof final TransactionResult.ExceedsMax exceeds) {
            definition.messages().find("exceeds-max").ifPresent(msg -> messages.send(
                    sender,
                    msg,
                    Map.of(
                            "player", targetName,
                            "max", BalanceSubcommand.formatAmount(exceeds.maxBalance(), definition)
                    )
            ));
            return;
        }
        if (result instanceof final TransactionResult.InvalidAmount invalid) {
            definition.messages().find("invalid-number").ifPresent(msg -> messages.send(
                    sender,
                    msg,
                    Map.of("input", invalid.reason())
            ));
            return;
        }
        definition.messages().find("transaction-failed").ifPresent(msg -> messages.send(
                sender,
                msg,
                Map.of("player", targetName)
        ));
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
