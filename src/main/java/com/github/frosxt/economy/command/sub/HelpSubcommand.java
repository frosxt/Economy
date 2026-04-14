package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.CommandDescriptor;
import com.github.frosxt.prisoncore.command.api.CommandResult;
import com.github.frosxt.prisoncore.command.api.PermissionPolicy;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class HelpSubcommand {
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final String permissionRoot;

    public HelpSubcommand(final CurrencyDefinition definition, final EconomyMessageDispatcher messages, final String permissionRoot) {
        this.definition = definition;
        this.messages = messages;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "help")
                .permission(PermissionPolicy.of(permissionRoot + ".help"))
                .description("Show help for this currency")
                .executor(ctx -> {
                    final CommandSender sender = (CommandSender) ctx.sender();
                    final boolean isAdmin = sender.hasPermission(permissionRoot + ".admin.give");
                    final String key = isAdmin ? "admin-help" : "player-help";
                    definition.messages().find(key).ifPresent(msg -> messages.send(sender, msg, Map.of()));

                    return new CommandResult.Success(null);
                })
                .build();
    }
}
