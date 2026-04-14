package com.github.frosxt.economy.storage.store;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.storage.backend.EconomyBackend;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

/**
 * Write-behind in-memory cache for per-player balances.
 * Reads are served from the in-memory map; writes update memory and mark the player dirty.
 * A periodic flush job drains dirty entries to the backing {@link EconomyBackend}.
 */
public final class BalanceStore {
    private final EconomyBackend backend;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, BigDecimal>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();
    private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
    private final ReentrantReadWriteLock scanLock = new ReentrantReadWriteLock();

    public BalanceStore(final EconomyBackend backend, final Logger logger) {
        this.backend = backend;
        this.logger = logger;
    }

    public Optional<BigDecimal> find(final UUID playerId, final CurrencyKey currency) {
        final Map<String, BigDecimal> entries = loadIfAbsent(playerId);
        return Optional.ofNullable(entries.get(currency.value()));
    }

    public void save(final UUID playerId, final CurrencyKey currency, final BigDecimal amount) {
        final ReentrantLock lock = lockFor(playerId);
        scanLock.readLock().lock();
        lock.lock();
        try {
            loadIfAbsent(playerId).put(currency.value(), amount);
            dirty.add(playerId);
        } finally {
            lock.unlock();
            scanLock.readLock().unlock();
        }
    }

    public Map<UUID, BigDecimal> allForCurrency(final CurrencyKey currency) {
        scanLock.writeLock().lock();
        try {
            flushInternal();
            final Map<UUID, BigDecimal> fromBackend = backend.loadAllForCurrency(currency);
            for (final Map.Entry<UUID, BigDecimal> entry : fromBackend.entrySet()) {
                cache.computeIfAbsent(entry.getKey(), id -> new ConcurrentHashMap<>())
                        .put(currency.value(), entry.getValue());
            }
            final Map<UUID, BigDecimal> out = new HashMap<>();
            for (final Map.Entry<UUID, ConcurrentHashMap<String, BigDecimal>> entry : cache.entrySet()) {
                final BigDecimal amount = entry.getValue().get(currency.value());
                if (amount != null) {
                    out.put(entry.getKey(), amount);
                }
            }
            return out;
        } finally {
            scanLock.writeLock().unlock();
        }
    }

    public void flush() {
        scanLock.readLock().lock();
        try {
            flushInternal();
        } finally {
            scanLock.readLock().unlock();
        }
    }

    public void shutdownFlush() {
        scanLock.writeLock().lock();
        try {
            flushInternal();
        } finally {
            scanLock.writeLock().unlock();
        }
    }

    private void flushInternal() {
        if (dirty.isEmpty()) {
            return;
        }
        final UUID[] snapshot = dirty.toArray(new UUID[0]);
        for (final UUID playerId : snapshot) {
            final ReentrantLock lock = lockFor(playerId);
            lock.lock();
            try {
                final ConcurrentHashMap<String, BigDecimal> entries = cache.get(playerId);
                if (entries == null) {
                    dirty.remove(playerId);
                    continue;
                }
                final Map<String, BigDecimal> copy = new HashMap<>(entries);
                try {
                    backend.saveBalances(playerId, copy);
                    dirty.remove(playerId);
                } catch (final RuntimeException ex) {
                    logger.warning("[Economy] flush failed for " + playerId + ": " + ex.getMessage());
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private Map<String, BigDecimal> loadIfAbsent(final UUID playerId) {
        return cache.computeIfAbsent(playerId, id -> {
            final Map<String, BigDecimal> loaded = backend.loadBalances(id);
            final ConcurrentHashMap<String, BigDecimal> map = new ConcurrentHashMap<>();
            map.putAll(loaded);
            return map;
        });
    }

    private ReentrantLock lockFor(final UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, id -> new ReentrantLock());
    }
}
