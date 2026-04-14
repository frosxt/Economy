package com.github.frosxt.economy.leaderboard;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.leaderboard.strategy.LeaderboardStrategy;
import com.github.frosxt.economy.storage.backend.EconomyBackend;
import com.github.frosxt.prisoncore.scheduler.api.TaskOrchestrator;
import com.github.frosxt.prisoncore.scheduler.api.TaskSpec;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class LeaderboardService {
    private final Map<CurrencyType, LeaderboardStrategy> strategies;
    private final EconomyBackend backend;
    private final TaskOrchestrator orchestrator;
    private final int maxEntries;
    private final Map<CurrencyKey, LeaderboardSnapshot> cached = new ConcurrentHashMap<>();

    public LeaderboardService(final Map<CurrencyType, LeaderboardStrategy> strategies, final EconomyBackend backend, final TaskOrchestrator orchestrator, final int maxEntries) {
        this.strategies = Map.copyOf(strategies);
        this.backend = backend;
        this.orchestrator = orchestrator;
        this.maxEntries = maxEntries;
    }

    public LeaderboardSnapshot current(final CurrencyDefinition definition) {
        final LeaderboardSnapshot snapshot = cached.get(definition.key());
        if (snapshot != null) {
            return snapshot;
        }
        final LeaderboardSnapshot fromDisk = backend.loadLeaderboard(definition.key())
                .orElse(LeaderboardSnapshot.empty(definition.key()));
        cached.put(definition.key(), fromDisk);
        return fromDisk;
    }

    public CompletableFuture<LeaderboardSnapshot> recalculate(final CurrencyDefinition definition) {
        final LeaderboardStrategy strategy = strategies.get(definition.type());
        if (strategy == null) {
            return CompletableFuture.completedFuture(LeaderboardSnapshot.empty(definition.key()));
        }

        final CompletableFuture<LeaderboardSnapshot> future = new CompletableFuture<>();
        orchestrator.io(TaskSpec.builder(() -> {
            try {
                final LeaderboardSnapshot snapshot = strategy.compute(definition, maxEntries);
                cached.put(definition.key(), snapshot);
                backend.saveLeaderboard(snapshot);
                future.complete(snapshot);
            } catch (final RuntimeException ex) {
                future.completeExceptionally(ex);
            }
        }).build());
        return future;
    }

    public void loadAllFromDisk(final Iterable<CurrencyDefinition> definitions) {
        for (final CurrencyDefinition definition : definitions) {
            backend.loadLeaderboard(definition.key())
                    .ifPresent(snapshot -> cached.put(definition.key(), snapshot));
        }
    }

    public void rebuildAll(final Iterable<CurrencyDefinition> definitions) {
        for (final CurrencyDefinition definition : definitions) {
            recalculate(definition);
        }
    }
}
