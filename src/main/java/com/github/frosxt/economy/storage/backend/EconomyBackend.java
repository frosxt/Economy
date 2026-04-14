package com.github.frosxt.economy.storage.backend;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.storage.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Backend contract for every persistent operation the Economy module performs.
 * All methods are called off the main thread and may block on I/O.
 * Aggregate-at-a-time shape: callers pass an entire player's balance map in one go,
 * so backends can serialize the whole document once per write.
 */
public interface EconomyBackend extends AutoCloseable {

    String name();

    Map<String, BigDecimal> loadBalances(UUID playerId);

    void saveBalances(UUID playerId, Map<String, BigDecimal> balances);

    Map<UUID, BigDecimal> loadAllForCurrency(CurrencyKey currency);

    void appendLedgerEntries(CurrencyKey currency, List<LedgerEntry> entries);

    List<LedgerEntry> loadRecentLedger(UUID playerId, CurrencyKey currency, int limit);

    Map<UUID, Boolean> loadPaymentToggles(CurrencyKey currency);

    void savePaymentToggle(UUID playerId, CurrencyKey currency, boolean enabled);

    Optional<LeaderboardSnapshot> loadLeaderboard(CurrencyKey currency);

    void saveLeaderboard(LeaderboardSnapshot snapshot);

    @Override
    void close() throws Exception;
}
