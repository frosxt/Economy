package com.github.frosxt.economy.api.banknote;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;

import java.math.BigDecimal;

/**
 * Outcome of redeeming a withdraw note item. Callers should check the variant
 * before consuming the item from the player's hand — only {@code Success}
 * means the balance was credited.
 */
public sealed interface WithdrawNoteRedeemResult {

    record Success(CurrencyKey currency, BigDecimal amount, BalanceSnapshot newBalance) implements WithdrawNoteRedeemResult {
    }

    /** Item did not carry this module's withdraw-note marker. Ignore it. */
    record NotAWithdrawNote() implements WithdrawNoteRedeemResult {
    }

    record AlreadyConsumed() implements WithdrawNoteRedeemResult {
    }

    record UnknownCurrency(CurrencyKey currency) implements WithdrawNoteRedeemResult {
    }

    record Failed(String reason) implements WithdrawNoteRedeemResult {
    }
}
