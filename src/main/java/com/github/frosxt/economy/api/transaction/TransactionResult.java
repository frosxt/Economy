package com.github.frosxt.economy.api.transaction;

import com.github.frosxt.economy.api.currency.CurrencyKey;

import java.math.BigDecimal;

/**
 * Outcome of any balance-mutating operation on {@link com.github.frosxt.economy.api.EconomyService}.
 * Callers should pattern-match on the sealed variants to react appropriately.
 */
public sealed interface TransactionResult {

    /** Operation succeeded. {@code newBalance} is the player's balance after the change. */
    record Success(BalanceSnapshot newBalance) implements TransactionResult {}

    /** Caller asked to withdraw/transfer more than the player has. */
    record InsufficientFunds(BigDecimal available, BigDecimal required) implements TransactionResult {}

    /** Operation would push the balance above the currency's configured max. */
    record ExceedsMax(BigDecimal currentBalance, BigDecimal maxBalance) implements TransactionResult {}

    /** Operation would push the balance below the currency's configured min. */
    record BelowMin(BigDecimal attempted, BigDecimal minBalance) implements TransactionResult {}

    /** Amount was null, non-positive, or the wrong shape for the currency. */
    record InvalidAmount(String reason) implements TransactionResult {}

    /** No currency is registered under the requested key. */
    record UnknownCurrency(CurrencyKey currency) implements TransactionResult {}

    /** Transfer attempted for a currency whose {@code transferable} rule is false. */
    record NotTransferable(CurrencyKey currency) implements TransactionResult {}

    /** Target player has toggled off incoming payments for this currency. */
    record PaymentsDisabled(java.util.UUID targetPlayer) implements TransactionResult {}

    /** Generic denial, e.g. EXP operations attempted against an offline player. */
    record Denied(String reason) implements TransactionResult {}
}
