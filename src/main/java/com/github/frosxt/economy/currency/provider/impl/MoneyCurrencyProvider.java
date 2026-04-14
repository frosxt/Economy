package com.github.frosxt.economy.currency.provider.impl;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;
import com.github.frosxt.economy.api.transaction.SetBalanceRequest;
import com.github.frosxt.economy.api.transaction.TransactionRequest;
import com.github.frosxt.economy.api.transaction.TransactionResult;
import com.github.frosxt.economy.currency.provider.CurrencyProvider;
import com.github.frosxt.economy.storage.store.BalanceStore;

import java.util.UUID;

public final class MoneyCurrencyProvider implements CurrencyProvider {
    private final VirtualCurrencyProvider delegate;

    public MoneyCurrencyProvider(final BalanceStore store) {
        this.delegate = new VirtualCurrencyProvider(store);
    }

    @Override
    public CurrencyType type() {
        return CurrencyType.MONEY;
    }

    @Override
    public BalanceSnapshot balance(final UUID playerId, final CurrencyDefinition definition) {
        return delegate.balance(playerId, definition);
    }

    @Override
    public TransactionResult deposit(final TransactionRequest request, final CurrencyDefinition definition) {
        return delegate.deposit(request, definition);
    }

    @Override
    public TransactionResult withdraw(final TransactionRequest request, final CurrencyDefinition definition) {
        return delegate.withdraw(request, definition);
    }

    @Override
    public TransactionResult set(final SetBalanceRequest request, final CurrencyDefinition definition) {
        return delegate.set(request, definition);
    }
}
