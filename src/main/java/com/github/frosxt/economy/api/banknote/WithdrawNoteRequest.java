package com.github.frosxt.economy.api.banknote;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to withdraw {@code amount} of {@code currency} from the player's
 * balance and hand back a physical note item. Debit happens first; on
 * success the note carries the value as NBT metadata.
 */
public record WithdrawNoteRequest(
        UUID playerId,
        CurrencyKey currency,
        BigDecimal amount
) {
}
