package com.github.frosxt.economy.vault;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.currency.CurrencyType;
import com.github.frosxt.economy.api.transaction.TransactionRequest;
import com.github.frosxt.economy.api.transaction.TransactionResult;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Bridges the Vault Economy interface to this module's MONEY currency.
 */
public final class VaultEconomyProvider implements Economy {
    private final EconomyService economy;
    private volatile CurrencyKey moneyKey;
    private volatile CurrencyDefinition moneyDefinition;

    public VaultEconomyProvider(final EconomyService economy) {
        this.economy = economy;
        refreshMoneyCurrency();
    }

    public void refreshMoneyCurrency() {
        for (final CurrencyDefinition definition : economy.currencies()) {
            if (definition.type() == CurrencyType.MONEY) {
                this.moneyKey = definition.key();
                this.moneyDefinition = definition;
                return;
            }
        }
        this.moneyKey = null;
        this.moneyDefinition = null;
    }

    @Override
    public boolean isEnabled() {
        return moneyKey != null;
    }

    @Override
    public String getName() {
        return "PrisonCore-Economy";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return moneyDefinition != null ? moneyDefinition.numberFormat().scale() : 2;
    }

    @Override
    public String format(final double amount) {
        if (moneyDefinition == null) {
            return Double.toString(amount);
        }
        final NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ROOT);
        formatter.setMinimumFractionDigits(moneyDefinition.numberFormat().scale());
        formatter.setMaximumFractionDigits(moneyDefinition.numberFormat().scale());
        formatter.setGroupingUsed(moneyDefinition.numberFormat().useGrouping());
        return formatter.format(amount);
    }

    @Override
    public String currencyNamePlural() {
        return moneyDefinition != null ? moneyDefinition.pluralName() : "coins";
    }

    @Override
    public String currencyNameSingular() {
        return moneyDefinition != null ? moneyDefinition.singularName() : "coin";
    }

    @Override
    public boolean hasAccount(final String playerName) {
        return Bukkit.getOfflinePlayer(playerName) != null;
    }

    @Override
    public boolean hasAccount(final OfflinePlayer player) {
        return player != null;
    }

    @Override
    public boolean hasAccount(final String playerName, final String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(final OfflinePlayer player, final String worldName) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(final String playerName) {
        final OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);
        return getBalance(player);
    }

    @Override
    public double getBalance(final OfflinePlayer player) {
        if (moneyKey == null || player == null) {
            return 0.0;
        }
        return economy.balance(player.getUniqueId(), moneyKey).amount().doubleValue();
    }

    @Override
    public double getBalance(final String playerName, final String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(final OfflinePlayer player, final String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(final String playerName, final double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(final OfflinePlayer player, final double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public boolean has(final String playerName, final String world, final double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(final OfflinePlayer player, final String world, final double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(final String playerName, final double amount) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(final OfflinePlayer player, final double amount) {
        if (moneyKey == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Money currency not configured");
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot withdraw negative amount");
        }
        final TransactionResult result = economy.withdraw(new TransactionRequest(
                player.getUniqueId(), moneyKey, BigDecimal.valueOf(amount), "vault-withdraw"));
        return toVaultResponse(result, amount, player);
    }

    @Override
    public EconomyResponse withdrawPlayer(final String playerName, final String world, final double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(final OfflinePlayer player, final String world, final double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(final String playerName, final double amount) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(final OfflinePlayer player, final double amount) {
        if (moneyKey == null) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Money currency not configured");
        }
        if (amount < 0) {
            return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, "Cannot deposit negative amount");
        }
        final TransactionResult result = economy.deposit(new TransactionRequest(
                player.getUniqueId(), moneyKey, BigDecimal.valueOf(amount), "vault-deposit"));
        return toVaultResponse(result, amount, player);
    }

    @Override
    public EconomyResponse depositPlayer(final String playerName, final String world, final double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(final OfflinePlayer player, final String world, final double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse createBank(final String name, final String player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse createBank(final String name, final OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse deleteBank(final String name) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankBalance(final String name) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankHas(final String name, final double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankWithdraw(final String name, final double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse bankDeposit(final String name, final double amount) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankOwner(final String name, final String playerName) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankOwner(final String name, final OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankMember(final String name, final String playerName) {
        return unsupportedBank();
    }

    @Override
    public EconomyResponse isBankMember(final String name, final OfflinePlayer player) {
        return unsupportedBank();
    }

    @Override
    public List<String> getBanks() {
        return Collections.emptyList();
    }

    @Override
    public boolean createPlayerAccount(final String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(final OfflinePlayer player) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(final String playerName, final String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(final OfflinePlayer player, final String worldName) {
        return true;
    }

    private EconomyResponse toVaultResponse(final TransactionResult result, final double amount, final OfflinePlayer player) {
        final double balance = getBalance(player);
        if (result instanceof TransactionResult.Success) {
            return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, "");
        }
        if (result instanceof TransactionResult.InsufficientFunds) {
            return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE,
                result.getClass().getSimpleName());
    }

    private EconomyResponse unsupportedBank() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED,
                "Bank accounts not supported");
    }
}
