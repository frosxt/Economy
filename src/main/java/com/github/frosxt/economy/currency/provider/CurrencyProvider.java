package com.github.frosxt.economy.currency.provider;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;
import com.github.frosxt.economy.api.transaction.SetBalanceRequest;
import com.github.frosxt.economy.api.transaction.TransactionRequest;
import com.github.frosxt.economy.api.transaction.TransactionResult;

public interface CurrencyProvider {
    CurrencyType type();

    BalanceSnapshot balance(java.util.UUID playerId, CurrencyDefinition definition);

    TransactionResult deposit(TransactionRequest request, CurrencyDefinition definition);

    TransactionResult withdraw(TransactionRequest request, CurrencyDefinition definition);

    TransactionResult set(SetBalanceRequest request, CurrencyDefinition definition);
}
