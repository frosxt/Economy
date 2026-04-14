package com.github.frosxt.economy.leaderboard.strategy;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.leaderboard.LeaderboardEntry;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.storage.store.BalanceStore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class BalanceBackedLeaderboardStrategy implements LeaderboardStrategy {
    private final BalanceStore store;

    public BalanceBackedLeaderboardStrategy(final BalanceStore store) {
        this.store = store;
    }

    @Override
    public LeaderboardSnapshot compute(final CurrencyDefinition definition, final int maxEntries) {
        final Instant start = Instant.now();
        final Map<UUID, BigDecimal> rawBalances = store.allForCurrency(definition.key());
        final List<Map.Entry<UUID, BigDecimal>> sorted = new ArrayList<>(rawBalances.entrySet());
        sorted.sort(Comparator.<Map.Entry<UUID, BigDecimal>, BigDecimal>comparing(Map.Entry::getValue).reversed());

        final int cap = maxEntries > 0 ? Math.min(maxEntries, sorted.size()) : sorted.size();
        final List<LeaderboardEntry> entries = new ArrayList<>(cap);
        for (int i = 0; i < cap; i++) {
            final Map.Entry<UUID, BigDecimal> entry = sorted.get(i);
            entries.add(new LeaderboardEntry(i + 1, entry.getKey(), resolveName(entry.getKey()), entry.getValue()));
        }

        return new LeaderboardSnapshot(
                definition.key(), entries, Instant.now(), Duration.between(start, Instant.now()));
    }

    private String resolveName(final UUID playerId) {
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        final String name = offline.getName();
        return name != null ? name : playerId.toString();
    }
}
