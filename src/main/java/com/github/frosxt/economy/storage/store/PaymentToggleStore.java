package com.github.frosxt.economy.storage.store;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.transaction.PaymentToggleState;
import com.github.frosxt.economy.storage.backend.EconomyBackend;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PaymentToggleStore {
    private final EconomyBackend backend;
    private final Map<String, Map<UUID, Boolean>> cache = new ConcurrentHashMap<>();

    public PaymentToggleStore(final EconomyBackend backend) {
        this.backend = backend;
    }

    public Optional<PaymentToggleState> find(final UUID playerId, final CurrencyKey currency) {
        final Map<UUID, Boolean> currencyMap = cache.computeIfAbsent(
                currency.value(), key -> new ConcurrentHashMap<>(backend.loadPaymentToggles(currency)));
        final Boolean enabled = currencyMap.get(playerId);
        if (enabled == null) {
            return Optional.empty();
        }
        return Optional.of(new PaymentToggleState(playerId, currency, enabled, Instant.now()));
    }

    public void save(final PaymentToggleState state) {
        cache.computeIfAbsent(state.currency().value(), key -> new ConcurrentHashMap<>())
                .put(state.playerId(), state.enabled());
        backend.savePaymentToggle(state.playerId(), state.currency(), state.enabled());
    }
}
