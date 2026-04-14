package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.leaderboard.render.LeaderboardMenuRenderer;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.CommandDescriptor;
import com.github.frosxt.prisoncore.command.api.CommandResult;
import com.github.frosxt.prisoncore.command.api.PermissionPolicy;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public final class TopSubcommand {
    private final EconomyService economy;
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final LeaderboardMenuRenderer leaderboardRenderer;
    private final String permissionRoot;

    public TopSubcommand(
            final EconomyService economy,
            final CurrencyDefinition definition,
            final EconomyMessageDispatcher messages,
            final LeaderboardMenuRenderer leaderboardRenderer,
            final String permissionRoot) {
        this.economy = economy;
        this.definition = definition;
        this.messages = messages;
        this.leaderboardRenderer = leaderboardRenderer;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "top")
                .permission(PermissionPolicy.playerOnly(permissionRoot + ".top"))
                .description("Open the leaderboard for this currency")
                .executor(ctx -> {
                    final CommandSender sender = (CommandSender) ctx.sender();
                    if (!(sender instanceof final Player player)) {
                        definition.messages().find("player-only").ifPresent(msg -> messages.send(sender, msg, Map.of()));
                        return new CommandResult.Success(null);
                    }

                    final LeaderboardSnapshot snapshot = economy.leaderboard(definition.key());
                    leaderboardRenderer.open(player, definition, snapshot);
                    return new CommandResult.Success(null);
                })
                .build();
    }
}
