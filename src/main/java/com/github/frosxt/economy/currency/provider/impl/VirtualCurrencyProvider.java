package com.github.frosxt.economy.currency.provider.impl;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;
import com.github.frosxt.economy.api.transaction.SetBalanceRequest;
import com.github.frosxt.economy.api.transaction.TransactionRequest;
import com.github.frosxt.economy.api.transaction.TransactionResult;
import com.github.frosxt.economy.currency.provider.CurrencyProvider;
import com.github.frosxt.economy.storage.store.BalanceStore;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class VirtualCurrencyProvider implements CurrencyProvider {
    private final BalanceStore store;
    private final ConcurrentHashMap<UUID, ReentrantLock> playerLocks = new ConcurrentHashMap<>();

    public VirtualCurrencyProvider(final BalanceStore store) {
        this.store = store;
    }

    @Override
    public CurrencyType type() {
        return CurrencyType.VIRTUAL;
    }

    @Override
    public BalanceSnapshot balance(final UUID playerId, final CurrencyDefinition definition) {
        final BigDecimal amount = store.find(playerId, definition.key()).orElse(definition.defaultBalance());
        return new BalanceSnapshot(playerId, definition.key(), amount, Instant.now());
    }

    @Override
    public TransactionResult deposit(final TransactionRequest request, final CurrencyDefinition definition) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            return new TransactionResult.InvalidAmount("amount must be positive");
        }

        final ReentrantLock lock = lockFor(request.playerId());
        lock.lock();
        try {
            final BigDecimal current = store.find(request.playerId(), definition.key())
                    .orElse(definition.defaultBalance());
            BigDecimal next = current.add(request.amount());
            if (definition.hasMaxBalance() && next.compareTo(definition.maxBalance()) > 0) {
                return new TransactionResult.ExceedsMax(current, definition.maxBalance());
            }
            next = clampScale(next, definition);
            store.save(request.playerId(), definition.key(), next);
            return new TransactionResult.Success(
                    new BalanceSnapshot(request.playerId(), definition.key(), next, Instant.now()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TransactionResult withdraw(final TransactionRequest request, final CurrencyDefinition definition) {
        if (request.amount() == null || request.amount().signum() <= 0) {
            return new TransactionResult.InvalidAmount("amount must be positive");
        }

        final ReentrantLock lock = lockFor(request.playerId());
        lock.lock();
        try {
            final BigDecimal current = store.find(request.playerId(), definition.key())
                    .orElse(definition.defaultBalance());
            if (current.compareTo(request.amount()) < 0) {
                return new TransactionResult.InsufficientFunds(current, request.amount());
            }
            BigDecimal next = current.subtract(request.amount());
            if (next.compareTo(definition.minBalance()) < 0) {
                return new TransactionResult.BelowMin(next, definition.minBalance());
            }
            next = clampScale(next, definition);
            store.save(request.playerId(), definition.key(), next);
            return new TransactionResult.Success(
                    new BalanceSnapshot(request.playerId(), definition.key(), next, Instant.now()));
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TransactionResult set(final SetBalanceRequest request, final CurrencyDefinition definition) {
        if (request.amount() == null) {
            return new TransactionResult.InvalidAmount("amount must not be null");
        }

        if (request.amount().compareTo(definition.minBalance()) < 0) {
            return new TransactionResult.BelowMin(request.amount(), definition.minBalance());
        }

        if (definition.hasMaxBalance() && request.amount().compareTo(definition.maxBalance()) > 0) {
            return new TransactionResult.ExceedsMax(request.amount(), definition.maxBalance());
        }

        final ReentrantLock lock = lockFor(request.playerId());
        lock.lock();
        try {
            final BigDecimal next = clampScale(request.amount(), definition);
            store.save(request.playerId(), definition.key(), next);
            return new TransactionResult.Success(
                    new BalanceSnapshot(request.playerId(), definition.key(), next, Instant.now()));
        } finally {
            lock.unlock();
        }
    }

    private ReentrantLock lockFor(final UUID playerId) {
        return playerLocks.computeIfAbsent(playerId, id -> new ReentrantLock());
    }

    private BigDecimal clampScale(final BigDecimal value, final CurrencyDefinition definition) {
        if (!definition.allowDecimals()) {
            return value.setScale(0, definition.numberFormat().roundMode().toBigDecimalMode());
        }
        return value.setScale(definition.scale(), definition.numberFormat().roundMode().toBigDecimalMode());
    }
}
