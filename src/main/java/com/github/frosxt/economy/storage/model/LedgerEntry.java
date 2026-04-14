package com.github.frosxt.economy.storage.model;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
        String id,
        Instant timestamp,
        CurrencyKey currency,
        UUID actor,
        UUID target,
        BigDecimal amount,
        String operation,
        String reason,
        BigDecimal preBalance,
        BigDecimal postBalance
) {
}
