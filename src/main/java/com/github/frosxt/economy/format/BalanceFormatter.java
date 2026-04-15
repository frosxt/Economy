package com.github.frosxt.economy.format;

import com.github.frosxt.economy.api.currency.NumberFormatPolicy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Centralised number formatting for currency amounts. Three styles are supported:
 *
 * <ul>
 *   <li>{@link #plain(BigDecimal)} — the unformatted plain-string form.</li>
 *   <li>{@link #grouped(BigDecimal, NumberFormatPolicy)} — comma-grouped digits at the policy's scale,
 *       e.g. {@code 103,039,284,576,378}.</li>
 *   <li>{@link #shortSuffix(BigDecimal, NumberFormatPolicy)} — short suffix form,
 *       e.g. {@code 1.2k}, {@code 4.7m}, {@code 8.5b}, {@code 3t}, {@code 12q}.</li>
 * </ul>
 *
 * {@link #format(BigDecimal, NumberFormatPolicy)} dispatches based on the policy's
 * {@code suffixesEnabled} flag: short-suffix when on, grouped when off.
 */
public final class BalanceFormatter {

    private BalanceFormatter() {
        throw new UnsupportedOperationException("Utility classes cannot be instantiated");
    }

    /** Suffix table, ordered from largest to smallest. Anything beyond decillion falls back to scientific notation. */
    private static final Suffix[] SUFFIXES = {
            new Suffix("d",  33), // decillion
            new Suffix("n",  30), // nonillion
            new Suffix("o",  27), // octillion
            new Suffix("S",  24), // septillion
            new Suffix("s",  21), // sextillion
            new Suffix("Q",  18), // quintillion
            new Suffix("q",  15), // quadrillion
            new Suffix("t",  12), // trillion
            new Suffix("b",  9),  // billion
            new Suffix("m",  6),  // million
            new Suffix("k",  3)   // thousand
    };

    /** @return the unformatted plain-digit string for {@code amount}. */
    public static String plain(final BigDecimal amount) {
        return amount == null ? "0" : amount.toPlainString();
    }

    /**
     * @return {@code amount} rounded to the policy's scale and rendered with comma grouping.
     *         Grouping is always on for this method regardless of the policy flag.
     */
    public static String grouped(final BigDecimal amount, final NumberFormatPolicy policy) {
        if (amount == null) {
            return "0";
        }
        final RoundingMode mode = policy.roundMode().toBigDecimalMode();
        final BigDecimal scaled = amount.setScale(policy.scale(), mode);
        final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.US);
        formatter.setGroupingUsed(true);
        formatter.setMinimumFractionDigits(policy.scale());
        formatter.setMaximumFractionDigits(policy.scale());
        return formatter.format(scaled);
    }

    /**
     * @return {@code amount} rendered as a short-suffix string (1.2k, 4.7m, 8.5b, 3t, 12q, ...).
     *         Fractional precision is two digits, trailing zeros stripped. Negative amounts are supported.
     *         Amounts under 1,000 fall through to {@link #grouped(BigDecimal, NumberFormatPolicy)}.
     */
    public static String shortSuffix(final BigDecimal amount, final NumberFormatPolicy policy) {
        if (amount == null) {
            return "0";
        }
        final BigDecimal abs = amount.abs();
        if (abs.compareTo(BigDecimal.valueOf(1000L)) < 0) {
            return grouped(amount, policy);
        }
        final RoundingMode mode = policy.roundMode().toBigDecimalMode();
        final String sign = amount.signum() < 0 ? "-" : "";
        for (final Suffix suffix : SUFFIXES) {
            final BigDecimal divisor = BigDecimal.TEN.pow(suffix.power());
            if (abs.compareTo(divisor) >= 0) {
                final BigDecimal scaled = abs.divide(divisor, 2, mode);
                return sign + stripTrailingZeros(scaled).toPlainString() + suffix.symbol();
            }
        }
        return grouped(amount, policy);
    }

    /**
     * Format {@code amount} using whichever style the {@link NumberFormatPolicy} requests.
     * Short-suffix when {@code suffixesEnabled} is true, grouped digits otherwise.
     */
    public static String format(final BigDecimal amount, final NumberFormatPolicy policy) {
        if (policy != null && policy.suffixesEnabled()) {
            return shortSuffix(amount, policy);
        }
        return grouped(amount, policy);
    }

    private static BigDecimal stripTrailingZeros(final BigDecimal value) {
        final BigDecimal stripped = value.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0, RoundingMode.UNNECESSARY) : stripped;
    }

    private record Suffix(String symbol, int power) {
    }
}
