package com.github.frosxt.economy.service;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.banknote.WithdrawNoteIssueResult;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRedeemResult;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRequest;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.api.transaction.*;
import com.github.frosxt.economy.currency.CurrencyRegistry;
import com.github.frosxt.economy.currency.provider.CurrencyProvider;
import com.github.frosxt.economy.leaderboard.LeaderboardService;
import com.github.frosxt.economy.storage.model.LedgerEntry;
import com.github.frosxt.economy.storage.store.LedgerStore;
import com.github.frosxt.economy.storage.store.PaymentToggleStore;
import com.github.frosxt.economy.withdraw.WithdrawNoteService;
import org.bukkit.inventory.ItemStack;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public final class DefaultEconomyService implements EconomyService {
    private final CurrencyRegistry registry;
    private final Map<CurrencyType, CurrencyProvider> providers;
    private final LedgerStore ledger;
    private final PaymentToggleStore paymentToggles;
    private final LeaderboardService leaderboardService;
    private final WithdrawNoteService withdrawNoteService;
    private final ConcurrentHashMap<UUID, ReentrantLock> transferLocks = new ConcurrentHashMap<>();

    public DefaultEconomyService(
            final CurrencyRegistry registry,
            final Map<CurrencyType, CurrencyProvider> providers,
            final LedgerStore ledger,
            final PaymentToggleStore paymentToggles,
            final LeaderboardService leaderboardService,
            final WithdrawNoteService withdrawNoteService) {
        this.registry = registry;
        this.providers = Map.copyOf(providers);
        this.ledger = ledger;
        this.paymentToggles = paymentToggles;
        this.leaderboardService = leaderboardService;
        this.withdrawNoteService = withdrawNoteService;
    }

    @Override
    public Optional<CurrencyDefinition> currency(final CurrencyKey key) {
        return registry.find(key);
    }

    @Override
    public Collection<CurrencyDefinition> currencies() {
        return registry.all();
    }

    @Override
    public BalanceSnapshot balance(final UUID playerId, final CurrencyKey key) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(key);
        if (definitionOpt.isEmpty()) {
            return BalanceSnapshot.zero(playerId, key);
        }

        final CurrencyDefinition definition = definitionOpt.get();
        final CurrencyProvider provider = providers.get(definition.type());
        if (provider == null) {
            return BalanceSnapshot.zero(playerId, key);
        }
        return provider.balance(playerId, definition);
    }

    @Override
    public boolean has(final UUID playerId, final CurrencyKey key, final BigDecimal amount) {
        final BalanceSnapshot snapshot = balance(playerId, key);
        return snapshot.amount().compareTo(amount) >= 0;
    }

    @Override
    public TransactionResult deposit(final TransactionRequest request) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(request.currency());
        if (definitionOpt.isEmpty()) {
            return new TransactionResult.UnknownCurrency(request.currency());
        }

        final CurrencyDefinition definition = definitionOpt.get();
        final CurrencyProvider provider = providers.get(definition.type());
        if (provider == null) {
            return new TransactionResult.UnknownCurrency(request.currency());
        }

        final BigDecimal preBalance = provider.balance(request.playerId(), definition).amount();
        final TransactionResult result = provider.deposit(request, definition);
        appendLedgerIfSuccess(result, definition, request.playerId(), request.playerId(), request.amount(), "deposit", request.reason(), preBalance);
        return result;
    }

    @Override
    public TransactionResult withdraw(final TransactionRequest request) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(request.currency());
        if (definitionOpt.isEmpty()) {
            return new TransactionResult.UnknownCurrency(request.currency());
        }
        final CurrencyDefinition definition = definitionOpt.get();
        final CurrencyProvider provider = providers.get(definition.type());
        if (provider == null) {
            return new TransactionResult.UnknownCurrency(request.currency());
        }
        final BigDecimal preBalance = provider.balance(request.playerId(), definition).amount();
        final TransactionResult result = provider.withdraw(request, definition);
        appendLedgerIfSuccess(result, definition, request.playerId(), request.playerId(),
                request.amount().negate(), "withdraw", request.reason(), preBalance);
        return result;
    }

    @Override
    public TransactionResult set(final SetBalanceRequest request) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(request.currency());
        if (definitionOpt.isEmpty()) {
            return new TransactionResult.UnknownCurrency(request.currency());
        }

        final CurrencyDefinition definition = definitionOpt.get();
        final CurrencyProvider provider = providers.get(definition.type());
        if (provider == null) {
            return new TransactionResult.UnknownCurrency(request.currency());
        }

        final BigDecimal preBalance = provider.balance(request.playerId(), definition).amount();
        final TransactionResult result = provider.set(request, definition);
        appendLedgerIfSuccess(result, definition, null, request.playerId(), request.amount(), "set", request.reason(), preBalance);
        return result;
    }

    @Override
    public TransactionResult transfer(final TransferRequest request) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(request.currency());
        if (definitionOpt.isEmpty()) {
            return new TransactionResult.UnknownCurrency(request.currency());
        }

        final CurrencyDefinition definition = definitionOpt.get();
        if (!definition.transferable()) {
            return new TransactionResult.NotTransferable(request.currency());
        }

        if (definition.payToggleEnabled()) {
            final PaymentToggleState toggle = paymentToggle(request.toPlayer(), request.currency());
            if (!toggle.enabled()) {
                return new TransactionResult.PaymentsDisabled(request.toPlayer());
            }
        }

        if (request.amount() == null || request.amount().signum() <= 0) {
            return new TransactionResult.InvalidAmount("amount must be positive");
        }

        final UUID first = lesser(request.fromPlayer(), request.toPlayer());
        final UUID second = first.equals(request.fromPlayer()) ? request.toPlayer() : request.fromPlayer();
        final ReentrantLock firstLock = transferLocks.computeIfAbsent(first, id -> new ReentrantLock());
        final ReentrantLock secondLock = transferLocks.computeIfAbsent(second, id -> new ReentrantLock());

        firstLock.lock();
        try {
            secondLock.lock();
            try {
                final TransactionRequest withdrawReq = new TransactionRequest(
                        request.fromPlayer(), request.currency(), request.amount(), request.reason());
                final TransactionResult withdrawResult = withdraw(withdrawReq);
                if (!(withdrawResult instanceof TransactionResult.Success)) {
                    return withdrawResult;
                }

                final TransactionRequest depositReq = new TransactionRequest(
                        request.toPlayer(), request.currency(), request.amount(), request.reason());
                final TransactionResult depositResult = deposit(depositReq);
                if (!(depositResult instanceof TransactionResult.Success)) {
                    final TransactionRequest rollback = new TransactionRequest(
                            request.fromPlayer(), request.currency(), request.amount(),
                            "rollback:" + request.reason());
                    deposit(rollback);
                    return depositResult;
                }
                return depositResult;
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    @Override
    public PaymentToggleState paymentToggle(final UUID playerId, final CurrencyKey key) {
        return paymentToggles.find(playerId, key).orElse(PaymentToggleState.enabledFor(playerId, key));
    }

    @Override
    public void setPaymentToggle(final UUID playerId, final CurrencyKey key, final boolean enabled) {
        paymentToggles.save(new PaymentToggleState(playerId, key, enabled, Instant.now()));
    }

    @Override
    public LeaderboardSnapshot leaderboard(final CurrencyKey key) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(key);
        return definitionOpt
                .map(leaderboardService::current)
                .orElseGet(() -> LeaderboardSnapshot.empty(key));
    }

    @Override
    public CompletableFuture<LeaderboardSnapshot> recalculateLeaderboard(final CurrencyKey key) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(key);
        return definitionOpt.map(leaderboardService::recalculate).orElseGet(() -> CompletableFuture.completedFuture(LeaderboardSnapshot.empty(key)));
    }

    @Override
    public WithdrawNoteIssueResult issueWithdrawNote(final WithdrawNoteRequest request) {
        final Optional<CurrencyDefinition> definitionOpt = registry.find(request.currency());
        if (definitionOpt.isEmpty()) {
            return new WithdrawNoteIssueResult.UnknownCurrency(request.currency());
        }

        final CurrencyDefinition definition = definitionOpt.get();
        if (!definition.withdrawEnabled() || !definition.withdrawItem().enabled()) {
            return new WithdrawNoteIssueResult.WithdrawDisabled(request.currency());
        }

        if (request.amount() == null || request.amount().signum() <= 0) {
            return new WithdrawNoteIssueResult.InvalidAmount("amount must be positive");
        }

        final TransactionResult withdrawResult = withdraw(new TransactionRequest(request.playerId(), request.currency(), request.amount(), "withdraw-note"));
        if (withdrawResult instanceof TransactionResult.InsufficientFunds) {
            return new WithdrawNoteIssueResult.InsufficientFunds();
        }

        if (!(withdrawResult instanceof final TransactionResult.Success success)) {
            return new WithdrawNoteIssueResult.Failed(withdrawResult.getClass().getSimpleName());
        }
        return withdrawNoteService.issue(request, definition, success.newBalance());
    }

    @Override
    public WithdrawNoteRedeemResult redeemWithdrawNote(final UUID playerId, final ItemStack item) {
        return withdrawNoteService.redeem(playerId, item, this);
    }

    private void appendLedgerIfSuccess(
            final TransactionResult result,
            final CurrencyDefinition definition,
            final UUID actor,
            final UUID target,
            final BigDecimal amount,
            final String operation,
            final String reason,
            final BigDecimal preBalance) {
        if (!(result instanceof final TransactionResult.Success success)) {
            return;
        }
        final LedgerEntry entry = new LedgerEntry(
                UUID.randomUUID().toString(),
                Instant.now(),
                definition.key(),
                actor,
                target,
                amount,
                operation,
                reason,
                preBalance,
                success.newBalance().amount()
        );
        ledger.append(entry);
    }

    private UUID lesser(final UUID a, final UUID b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
