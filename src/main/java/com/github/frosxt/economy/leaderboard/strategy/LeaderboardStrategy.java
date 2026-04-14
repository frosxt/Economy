package com.github.frosxt.economy.leaderboard.strategy;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;

public sealed interface LeaderboardStrategy permits BalanceBackedLeaderboardStrategy {
    LeaderboardSnapshot compute(CurrencyDefinition definition, int maxEntries);
}
