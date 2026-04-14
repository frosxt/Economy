package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.transaction.TransactionResult;
import com.github.frosxt.economy.api.transaction.TransferRequest;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.CommandDescriptor;
import com.github.frosxt.prisoncore.command.api.CommandResult;
import com.github.frosxt.prisoncore.command.api.PermissionPolicy;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class PaySubcommand {
    private final EconomyService economy;
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final String permissionRoot;

    public PaySubcommand(final EconomyService economy, final CurrencyDefinition definition, final EconomyMessageDispatcher messages, final String permissionRoot) {
        this.economy = economy;
        this.definition = definition;
        this.messages = messages;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "pay")
                .permission(PermissionPolicy.playerOnly(permissionRoot + ".pay"))
                .description("Pay another player from your balance")
                .completionProvider((ctx, argIndex) -> {
                    if (argIndex == 0) {
                        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    }
                    return List.of();
                })
                .executor(ctx -> {
                    final CommandSender rawSender = (CommandSender) ctx.sender();
                    if (!(rawSender instanceof final Player sender)) {
                        definition.messages().find("player-only").ifPresent(msg -> messages.send(rawSender, msg, Map.of()));
                        return new CommandResult.Success(null);
                    }

                    if (ctx.argCount() < 2) {
                        return new CommandResult.Usage("/" + ctx.label() + " pay <player> <amount>");
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

                    final Player target = Bukkit.getPlayerExact(targetName);
                    if (target == null) {
                        definition.messages().find("invalid-player").ifPresent(msg -> messages.send(
                                sender,
                                msg,
                                Map.of("player", targetName)
                        ));
                        return new CommandResult.Success(null);
                    }

                    if (target.getUniqueId().equals(sender.getUniqueId())) {
                        definition.messages().find("pay-self").ifPresent(msg -> messages.send(sender, msg, Map.of()));
                        return new CommandResult.Success(null);
                    }
                    final TransactionResult result = economy.transfer(new TransferRequest(
                            sender.getUniqueId(),
                            target.getUniqueId(),
                            definition.key(),
                            amount,
                            "player-pay"
                    ));
                    handleResult(sender, target, amount, result);
                    return new CommandResult.Success(null);
                })
                .build();
    }

    private void handleResult(final Player sender, final Player target, final BigDecimal amount, final TransactionResult result) {
        final String formatted = BalanceSubcommand.formatAmount(amount, definition);
        if (result instanceof TransactionResult.Success) {
            definition.messages().find("payment-sent").ifPresent(msg -> messages.send(
                    sender,
                    msg,
                    Map.of("player", target.getName(), "amount", formatted)
            ));
            definition.messages().find("payment-received").ifPresent(msg -> messages.send(
                    target,
                    msg,
                    Map.of("player", sender.getName(), "amount", formatted)
            ));
            return;
        }
        if (result instanceof TransactionResult.PaymentsDisabled) {
            definition.messages().find("payments-toggled").ifPresent(msg -> messages.send(
                    sender,
                    msg,
                    Map.of("player", target.getName())
            ));
            return;
        }
        if (result instanceof TransactionResult.NotTransferable) {
            definition.messages().find("not-transferable").ifPresent(msg -> messages.send(sender, msg, Map.of()));
            return;
        }
        if (result instanceof final TransactionResult.InsufficientFunds insufficient) {
            definition.messages().find("not-enough").ifPresent(msg -> messages.send(
                    sender,
                    msg,
                    Map.of(
                            "available", BalanceSubcommand.formatAmount(insufficient.available(), definition),
                            "required", BalanceSubcommand.formatAmount(insufficient.required(), definition)
                    )
            ));
            return;
        }
        if (result instanceof final TransactionResult.ExceedsMax exceeds) {
            definition.messages().find("exceeds-max").ifPresent(msg -> messages.send(
                    sender,
                    msg,
                    Map.of(
                            "player", target.getName(),
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
                Map.of("player", target.getName())
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
