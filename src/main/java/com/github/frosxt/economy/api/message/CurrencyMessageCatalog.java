package com.github.frosxt.economy.api.message;

import java.util.Map;
import java.util.Optional;

/**
 * Per-currency message catalog parsed from the {@code messages:} block of a currency YAML.
 * Keyed by short name (e.g. {@code balance}, {@code pay-self}).
 */
public record CurrencyMessageCatalog(Map<String, CurrencyMessage> messages) {

    public CurrencyMessageCatalog {
        messages = Map.copyOf(messages);
    }

    public Optional<CurrencyMessage> find(final String key) {
        return Optional.ofNullable(messages.get(key));
    }

    public static CurrencyMessageCatalog empty() {
        return new CurrencyMessageCatalog(Map.of());
    }
}
