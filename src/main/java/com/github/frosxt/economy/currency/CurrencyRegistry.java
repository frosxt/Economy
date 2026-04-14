package com.github.frosxt.economy.currency;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.util.*;

public final class CurrencyRegistry {
    private final Map<CurrencyKey, CurrencyDefinition> definitions = new LinkedHashMap<>();

    public synchronized void register(final CurrencyDefinition definition) {
        definitions.put(definition.key(), definition);
    }

    public synchronized Optional<CurrencyDefinition> find(final CurrencyKey key) {
        return Optional.ofNullable(definitions.get(key));
    }

    public synchronized Collection<CurrencyDefinition> all() {
        return Collections.unmodifiableCollection(new LinkedHashMap<>(definitions).values());
    }

    public synchronized void clear() {
        definitions.clear();
    }

    public synchronized int size() {
        return definitions.size();
    }
}
