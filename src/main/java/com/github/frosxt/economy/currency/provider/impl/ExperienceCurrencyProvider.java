package com.github.frosxt.economy.currency.provider.impl;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;
import com.github.frosxt.economy.api.transaction.SetBalanceRequest;
import com.github.frosxt.economy.api.transaction.TransactionRequest;
import com.github.frosxt.economy.api.transaction.TransactionResult;
import com.github.frosxt.economy.currency.provider.CurrencyProvider;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ExperienceCurrencyProvider implements CurrencyProvider {

    @Override
    public CurrencyType type() {
        return CurrencyType.EXP;
    }

    @Override
    public BalanceSnapshot balance(final UUID playerId, final CurrencyDefinition definition) {
        requireMainThread();
        final Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return new BalanceSnapshot(playerId, definition.key(), BigDecimal.ZERO, Instant.now());
        }

        final int totalXp = player.getTotalExperience();
        return new BalanceSnapshot(playerId, definition.key(), BigDecimal.valueOf(totalXp), Instant.now());
    }

    @Override
    public TransactionResult deposit(final TransactionRequest request, final CurrencyDefinition definition) {
        requireMainThread();
        if (!isWholeNumber(request.amount())) {
            return new TransactionResult.InvalidAmount("exp amount must be a whole number");
        }

        if (request.amount().signum() <= 0) {
            return new TransactionResult.InvalidAmount("amount must be positive");
        }

        final Player player = Bukkit.getPlayer(request.playerId());
        if (player == null) {
            return new TransactionResult.Denied("player is not online");
        }

        final int toAdd = request.amount().intValueExact();
        player.giveExp(toAdd);

        return new TransactionResult.Success(captureCurrentBalance(player, definition));
    }

    @Override
    public TransactionResult withdraw(final TransactionRequest request, final CurrencyDefinition definition) {
        requireMainThread();

        if (!isWholeNumber(request.amount())) {
            return new TransactionResult.InvalidAmount("exp amount must be a whole number");
        }

        if (request.amount().signum() <= 0) {
            return new TransactionResult.InvalidAmount("amount must be positive");
        }

        final Player player = Bukkit.getPlayer(request.playerId());
        if (player == null) {
            return new TransactionResult.Denied("player is not online");
        }

        final int currentTotal = player.getTotalExperience();
        final int toRemove = request.amount().intValueExact();
        if (currentTotal < toRemove) {
            return new TransactionResult.InsufficientFunds(
                    BigDecimal.valueOf(currentTotal), request.amount());
        }
        applyTotalExperience(player, currentTotal - toRemove);

        return new TransactionResult.Success(captureCurrentBalance(player, definition));
    }

    @Override
    public TransactionResult set(final SetBalanceRequest request, final CurrencyDefinition definition) {
        requireMainThread();

        if (!isWholeNumber(request.amount())) {
            return new TransactionResult.InvalidAmount("exp amount must be a whole number");
        }

        if (request.amount().signum() < 0) {
            return new TransactionResult.BelowMin(request.amount(), BigDecimal.ZERO);
        }

        final Player player = Bukkit.getPlayer(request.playerId());
        if (player == null) {
            return new TransactionResult.Denied("player is not online");
        }
        applyTotalExperience(player, request.amount().intValueExact());

        return new TransactionResult.Success(captureCurrentBalance(player, definition));
    }

    private void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) {throw new IllegalStateException("ExperienceCurrencyProvider called from async thread '" + Thread.currentThread().getName() + "' — EXP currency requires the Bukkit main thread.");
        }
    }

    private BalanceSnapshot captureCurrentBalance(final Player player, final CurrencyDefinition definition) {
        return new BalanceSnapshot(player.getUniqueId(), definition.key(), BigDecimal.valueOf(player.getTotalExperience()), Instant.now()
        );
    }

    private void applyTotalExperience(final Player player, final int totalXp) {
        player.setExp(0.0f);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(Math.max(0, totalXp));
    }

    private boolean isWholeNumber(final BigDecimal value) {
        if (value == null) {
            return false;
        }
        return value.stripTrailingZeros().scale() <= 0;
    }
}
