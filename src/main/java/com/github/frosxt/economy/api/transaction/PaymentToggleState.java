package com.github.frosxt.economy.api.transaction;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.time.Instant;
import java.util.UUID;

/**
 * Whether a player currently accepts incoming payments for a specific currency.
 * Absence of a record is treated as "enabled" — the toggle is opt-out.
 */
public record PaymentToggleState(
        UUID playerId,
        CurrencyKey currency,
        boolean enabled,
        Instant updatedAt
) {

    public static PaymentToggleState enabledFor(final UUID playerId, final CurrencyKey currency) {
        return new PaymentToggleState(playerId, currency, true, Instant.now());
    }

    public static PaymentToggleState disabledFor(final UUID playerId, final CurrencyKey currency) {
        return new PaymentToggleState(playerId, currency, false, Instant.now());
    }
}
