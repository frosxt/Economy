package com.github.frosxt.economy.api.leaderboard;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Immutable top-N ranking for a currency at a specific instant.
 * Entries are sorted descending by balance; the length is capped by
 * {@code leaderboard.max-entries} in the module config.
 */
public record LeaderboardSnapshot(
        CurrencyKey currency,
        List<LeaderboardEntry> entries,
        Instant generatedAt,
        Duration generationDuration
) {

    public LeaderboardSnapshot {
        entries = List.copyOf(entries);
    }

    public static LeaderboardSnapshot empty(final CurrencyKey currency) {
        return new LeaderboardSnapshot(currency, Collections.emptyList(), Instant.now(), Duration.ZERO);
    }

    /** @return the entry for {@code playerId}, or empty if they are not in this snapshot. */
    public Optional<LeaderboardEntry> findByPlayer(final java.util.UUID playerId) {
        for (final LeaderboardEntry entry : entries) {
            if (entry.playerId().equals(playerId)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }
}
