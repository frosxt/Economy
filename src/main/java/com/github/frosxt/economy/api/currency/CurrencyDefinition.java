package com.github.frosxt.economy.api.currency;

import com.github.frosxt.economy.api.banknote.WithdrawItemTemplate;
import com.github.frosxt.economy.api.menu.CurrencyMenuCatalog;
import com.github.frosxt.economy.api.message.CurrencyMessageCatalog;

import java.math.BigDecimal;

/**
 * Immutable, fully-resolved currency configuration loaded from one
 * {@code currencies/<key>.yml} file. Carries display metadata, transaction rules,
 * balance bounds, the command layout, withdraw-note template, menu catalog, and
 * message catalog for a single currency.
 */
public record CurrencyDefinition(
        CurrencyKey key,
        CurrencyType type,
        String displayName,
        String singularName,
        String pluralName,
        int scale,
        boolean allowDecimals,
        boolean transferable,
        boolean withdrawEnabled,
        boolean leaderboardEnabled,
        boolean payToggleEnabled,
        BigDecimal defaultBalance,
        BigDecimal minBalance,
        BigDecimal maxBalance,
        NumberFormatPolicy numberFormat,
        CommandDefinition command,
        WithdrawItemTemplate withdrawItem,
        CurrencyMenuCatalog menus,
        CurrencyMessageCatalog messages
) {

    /** @return {@code true} when the currency has a finite upper bound; {@code -1} means unlimited. */
    public boolean hasMaxBalance() {
        return maxBalance != null && maxBalance.signum() >= 0;
    }
}
