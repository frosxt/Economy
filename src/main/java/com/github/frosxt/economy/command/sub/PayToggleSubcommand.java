package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.transaction.PaymentToggleState;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.CommandDescriptor;
import com.github.frosxt.prisoncore.command.api.CommandResult;
import com.github.frosxt.prisoncore.command.api.PermissionPolicy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class PayToggleSubcommand {
    private final EconomyService economy;
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final String permissionRoot;

    public PayToggleSubcommand(final EconomyService economy, final CurrencyDefinition definition, final EconomyMessageDispatcher messages, final String permissionRoot) {
        this.economy = economy;
        this.definition = definition;
        this.messages = messages;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "paytoggle")
                .permission(PermissionPolicy.playerOnly(permissionRoot + ".paytoggle"))
                .description("Toggle whether you accept incoming payments")
                .executor(ctx -> {
                    final CommandSender sender = (CommandSender) ctx.sender();
                    if (!(sender instanceof final Player player)) {
                        definition.messages().find("player-only").ifPresent(msg -> messages.send(sender, msg, Map.of()));
                        return new CommandResult.Success(null);
                    }

                    final PaymentToggleState current = economy.paymentToggle(player.getUniqueId(), definition.key());
                    final boolean nextEnabled = !current.enabled();
                    economy.setPaymentToggle(player.getUniqueId(), definition.key(), nextEnabled);

                    final String key = nextEnabled ? "payments-enabled" : "payments-disabled";
                    definition.messages().find(key).ifPresent(msg -> messages.send(player, msg, Map.of()));
                    return new CommandResult.Success(null);
                })
                .build();
    }
}
