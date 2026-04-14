package com.github.frosxt.economy.api;

import com.github.frosxt.economy.api.banknote.WithdrawNoteIssueResult;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRedeemResult;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRequest;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.api.transaction.*;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Public API for the Economy module. External modules depending on Economy should
 * resolve this type from {@code ModuleContext.services()} and never touch internal
 * implementation classes.
 */
public interface EconomyService {

    /** @return the currency definition for {@code key}, or empty if unknown. */
    Optional<CurrencyDefinition> currency(CurrencyKey key);

    /** @return every registered currency. */
    Collection<CurrencyDefinition> currencies();

    /** Read a player's current balance for a currency. Always returns a snapshot, never null. */
    BalanceSnapshot balance(UUID playerId, CurrencyKey key);

    /** @return {@code true} if the player's balance is at least {@code amount}. */
    boolean has(UUID playerId, CurrencyKey key, BigDecimal amount);

    /** Credit a player's balance. Respects max-balance and currency rules. */
    TransactionResult deposit(TransactionRequest request);

    /** Debit a player's balance. Fails with {@code InsufficientFunds} if under-funded. */
    TransactionResult withdraw(TransactionRequest request);

    /** Overwrite a player's balance to the requested amount, subject to min/max rules. */
    TransactionResult set(SetBalanceRequest request);

    /**
     * Transfer from one player to another atomically under deterministic lock ordering.
     * Honours the recipient's pay-toggle and the currency's {@code transferable} rule.
     */
    TransactionResult transfer(TransferRequest request);

    /** @return whether the player currently accepts incoming payments for the currency. */
    PaymentToggleState paymentToggle(UUID playerId, CurrencyKey key);

    void setPaymentToggle(UUID playerId, CurrencyKey key, boolean enabled);

    /** @return the most recent cached leaderboard. Recalculated periodically by the module. */
    LeaderboardSnapshot leaderboard(CurrencyKey key);

    /** Trigger an off-thread recalculation and return a future that completes with the new snapshot. */
    CompletableFuture<LeaderboardSnapshot> recalculateLeaderboard(CurrencyKey key);

    /** Convert part of a player's balance into a physical withdraw note item. */
    WithdrawNoteIssueResult issueWithdrawNote(WithdrawNoteRequest request);

    /** Credit the player the value encoded in a withdraw note {@link ItemStack}, if valid. */
    WithdrawNoteRedeemResult redeemWithdrawNote(UUID playerId, ItemStack item);
}
