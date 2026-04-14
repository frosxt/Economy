package com.github.frosxt.economy.api.transaction;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single-player balance change request (deposit or withdraw). The {@code reason}
 * is free-form and flows through to the ledger entry for audit.
 */
public record TransactionRequest(
        UUID playerId,
        CurrencyKey currency,
        BigDecimal amount,
        String reason
) {

    public TransactionRequest withAmount(final BigDecimal newAmount) {
        return new TransactionRequest(playerId, currency, newAmount, reason);
    }
}
