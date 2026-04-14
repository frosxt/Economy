package com.github.frosxt.economy.command.sub;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.message.EconomyMessageDispatcher;
import com.github.frosxt.prisoncore.command.api.CommandDescriptor;
import com.github.frosxt.prisoncore.command.api.CommandResult;
import com.github.frosxt.prisoncore.command.api.PermissionPolicy;
import com.github.frosxt.prisoncore.scheduler.api.TaskOrchestrator;

import java.util.Map;

public final class RecalcSubcommand {
    private final EconomyService economy;
    private final CurrencyDefinition definition;
    private final EconomyMessageDispatcher messages;
    private final TaskOrchestrator orchestrator;
    private final String permissionRoot;

    public RecalcSubcommand(
            final EconomyService economy,
            final CurrencyDefinition definition,
            final EconomyMessageDispatcher messages,
            final TaskOrchestrator orchestrator,
            final String permissionRoot) {
        this.economy = economy;
        this.definition = definition;
        this.messages = messages;
        this.orchestrator = orchestrator;
        this.permissionRoot = permissionRoot;
    }

    public CommandDescriptor build(final String namespace) {
        return CommandDescriptor.builder(namespace, "recalc")
                .permission(PermissionPolicy.of(permissionRoot + ".recalc"))
                .description("Recalculate the leaderboard for this currency")
                .executor(ctx -> {
                    definition.messages().find("top-updating").ifPresent(msg -> messages.broadcast(msg, Map.of()));
                    final long startedAt = System.currentTimeMillis();
                    economy.recalculateLeaderboard(definition.key()).thenAccept(snapshot ->
                            orchestrator.switchToMainThread().thenRun(() -> {
                                final long elapsed = System.currentTimeMillis() - startedAt;
                                definition.messages().find("top-updated").ifPresent(msg -> messages.broadcast(
                                        msg,
                                        Map.of("time", String.valueOf(elapsed))
                                ));
                            }));
                    return new CommandResult.Success(null);
                })
                .build();
    }
}
