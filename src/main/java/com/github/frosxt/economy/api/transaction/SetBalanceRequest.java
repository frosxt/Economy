package com.github.frosxt.economy.api.transaction;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Overwrite a player's balance to a specific value. Subject to the currency's
 * min/max rules the same way deposits and withdraws are.
 */
public record SetBalanceRequest(
        UUID playerId,
        CurrencyKey currency,
        BigDecimal amount,
        String reason
) {
}
