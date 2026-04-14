package com.github.frosxt.economy.api.transaction;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Atomic player-to-player transfer. The economy service acquires per-player locks
 * in canonical UUID order to prevent deadlocks and rolls back the debit if the
 * credit fails.
 */
public record TransferRequest(
        UUID fromPlayer,
        UUID toPlayer,
        CurrencyKey currency,
        BigDecimal amount,
        String reason
) {
}
