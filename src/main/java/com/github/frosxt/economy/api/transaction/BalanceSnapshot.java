package com.github.frosxt.economy.api.transaction;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Point-in-time view of a single player's balance for one currency.
 * The {@code capturedAt} stamp lets callers reason about read freshness
 * when the underlying balance store may be updating concurrently.
 */
public record BalanceSnapshot(
        UUID playerId,
        CurrencyKey currency,
        BigDecimal amount,
        Instant capturedAt
) {

    public static BalanceSnapshot zero(final UUID playerId, final CurrencyKey currency) {
        return new BalanceSnapshot(playerId, currency, BigDecimal.ZERO, Instant.now());
    }
}
