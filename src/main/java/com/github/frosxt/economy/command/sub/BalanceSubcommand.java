package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;
import com.github.frosxt.economy.format.BalanceFormatter;
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
import java.util.UUID;

public final class BalanceSubcommand {
    private final EconomyService economy;
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final String permissionRoot;

    public BalanceSubcommand(final EconomyService economy, final CurrencyDefinition definition, final EconomyMessageDispatcher messages, final String permissionRoot) {
        this.economy = economy;
        this.definition = definition;
        this.messages = messages;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "balance")
                .aliases("bal")
                .permission(PermissionPolicy.of(permissionRoot + ".balance"))
                .description("Show your balance or another player's balance")
                .completionProvider((ctx, argIndex) -> {
                    if (argIndex == 0) {
                        return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                    }
                    return List.of();
                })
                .executor(ctx -> {
                    final CommandSender sender = (CommandSender) ctx.sender();
                    if (ctx.argCount() == 0) {
                        return executeSelf(sender, ctx.senderId());
                    }
                    return executeOther(sender, ctx.arg(0));
                })
                .build();
    }

    private CommandResult executeSelf(final CommandSender sender, final UUID senderId) {
        if (senderId == null) {
            definition.messages().find("player-only")
                    .ifPresent(msg -> messages.send(sender, msg, Map.of()));
            return new CommandResult.Success(null);
        }

        final BalanceSnapshot snapshot = economy.balance(senderId, definition.key());
        final String formatted = formatAmount(snapshot.amount(), definition);
        definition.messages().find("balance")
                .ifPresent(msg -> messages.send(sender, msg, Map.of("amount", formatted)));
        return new CommandResult.Success(null);
    }

    private CommandResult executeOther(final CommandSender sender, final String targetName) {
        if (!sender.hasPermission(permissionRoot + ".balance.others")) {
            definition.messages().find("no-permission")
                    .ifPresent(msg -> messages.send(sender, msg, Map.of()));
            return new CommandResult.Success(null);
        }

        final OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        if (!target.hasPlayedBefore() && Bukkit.getPlayerExact(targetName) == null) {
            definition.messages().find("invalid-player")
                    .ifPresent(msg -> messages.send(sender, msg, Map.of("player", targetName)));
            return new CommandResult.Success(null);
        }

        final BalanceSnapshot snapshot = economy.balance(target.getUniqueId(), definition.key());
        final String formatted = formatAmount(snapshot.amount(), definition);
        definition.messages().find("balance-other").ifPresent(msg -> messages.send(
                sender,
                msg,
                Map.of("player", targetName, "amount", formatted)
        ));
        return new CommandResult.Success(null);
    }

    static String formatAmount(final BigDecimal amount, final CurrencyDefinition definition) {
        return BalanceFormatter.format(amount, definition.numberFormat());
    }
}
