package com.github.frosxt.economy.api.banknote;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;
import org.bukkit.inventory.ItemStack;

/**
 * Outcome of {@link com.github.frosxt.economy.api.EconomyService#issueWithdrawNote}.
 * {@code Success} carries both the physical item to hand to the player and the
 * post-debit balance snapshot.
 */
public sealed interface WithdrawNoteIssueResult {

    record Success(ItemStack note, BalanceSnapshot newBalance) implements WithdrawNoteIssueResult {}

    /** The currency forbids withdrawal, or its withdraw-item template is disabled. */
    record WithdrawDisabled(CurrencyKey currency) implements WithdrawNoteIssueResult {}

    record InsufficientFunds() implements WithdrawNoteIssueResult {}

    record InvalidAmount(String reason) implements WithdrawNoteIssueResult {}

    record UnknownCurrency(CurrencyKey currency) implements WithdrawNoteIssueResult {}

    record Failed(String reason) implements WithdrawNoteIssueResult {}
}
