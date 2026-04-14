package com.github.frosxt.economy.storage.store;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.storage.backend.EconomyBackend;
import com.github.frosxt.economy.storage.model.LedgerEntry;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Buffered ledger writer. Append calls enqueue to a per-currency bounded queue;
 * the flush pass drains each queue into one backend batch call.
 */
public final class LedgerStore {
    private static final int QUEUE_CAPACITY = 1024;
    private static final int DRAIN_BATCH_MAX = 256;

    private final EconomyBackend backend;
    private final Logger logger;
    private final Map<String, BlockingQueue<LedgerEntry>> queues = new ConcurrentHashMap<>();

    public LedgerStore(final EconomyBackend backend, final Logger logger) {
        this.backend = backend;
        this.logger = logger;
    }

    public void append(final LedgerEntry entry) {
        final BlockingQueue<LedgerEntry> queue = queues.computeIfAbsent(
                entry.currency().value(), key -> new ArrayBlockingQueue<>(QUEUE_CAPACITY));
        if (!queue.offer(entry)) {
            synchronized (queue) {
                backend.appendLedgerEntries(entry.currency(), Collections.singletonList(entry));
            }
            logger.warning("[Economy] ledger queue saturated for " + entry.currency().value() + ", wrote directly");
        }
    }

    public List<LedgerEntry> loadRecent(final UUID playerId, final CurrencyKey currency, final int limit) {
        return backend.loadRecentLedger(playerId, currency, limit);
    }

    public void flush() {
        for (final Map.Entry<String, BlockingQueue<LedgerEntry>> entry : queues.entrySet()) {
            drainQueue(entry.getValue());
        }
    }

    public void shutdownFlush() {
        flush();
    }

    private void drainQueue(final BlockingQueue<LedgerEntry> queue) {
        if (queue.isEmpty()) {
            return;
        }
        final List<LedgerEntry> batch = new ArrayList<>();
        queue.drainTo(batch, DRAIN_BATCH_MAX);
        if (batch.isEmpty()) {
            return;
        }
        final CurrencyKey currency = batch.get(0).currency();
        try {
            backend.appendLedgerEntries(currency, batch);
        } catch (final RuntimeException ex) {
            logger.warning("[Economy] ledger flush failed for " + currency.value() + ": " + ex.getMessage());
        }
    }
}
