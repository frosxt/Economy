package com.github.frosxt.economy.api.currency;

/**
 * How a currency's amounts are rounded and displayed. Drives the scale of the
 * underlying {@link java.math.BigDecimal}, thousands grouping, and (future)
 * short-form suffixes like {@code 1.2k}.
 */
public record NumberFormatPolicy(
        int scale,
        boolean useGrouping,
        boolean suffixesEnabled,
        RoundMode roundMode
) {

    public static NumberFormatPolicy defaultPolicy() {
        return new NumberFormatPolicy(2, true, false, RoundMode.DOWN);
    }
}
