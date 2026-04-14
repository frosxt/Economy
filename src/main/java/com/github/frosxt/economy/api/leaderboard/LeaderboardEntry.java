package com.github.frosxt.economy.api.leaderboard;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One ranked row of a {@link LeaderboardSnapshot}. The {@code rank} is 1-based.
 * The {@code username} is resolved via Bukkit at snapshot time and may be stale.
 */
public record LeaderboardEntry(
        int rank,
        UUID playerId,
        String username,
        BigDecimal amount
) {
}
