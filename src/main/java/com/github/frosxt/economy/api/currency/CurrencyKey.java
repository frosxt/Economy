package com.github.frosxt.economy.api.currency;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Identifier for a currency. The value must match {@code [a-z0-9][a-z0-9_-]{0,63}},
 * enforced at construction to make it safe to use as a filesystem/storage key.
 */
public record CurrencyKey(String value) {

    private static final Pattern SAFE = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    public CurrencyKey {
        Objects.requireNonNull(value, "value");
        if (!SAFE.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "currency key must match [a-z0-9_-], start with alphanumeric, and be 1..64 chars: " + value);
        }
    }

    public static CurrencyKey of(final String value) {
        return new CurrencyKey(value == null ? "" : value.toLowerCase());
    }
}
