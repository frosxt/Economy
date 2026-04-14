package com.github.frosxt.economy.withdraw;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.banknote.WithdrawItemTemplate;
import com.github.frosxt.economy.api.banknote.WithdrawNoteIssueResult;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRedeemResult;
import com.github.frosxt.economy.api.banknote.WithdrawNoteRequest;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.transaction.BalanceSnapshot;
import com.github.frosxt.economy.api.transaction.TransactionRequest;
import com.github.frosxt.economy.api.transaction.TransactionResult;
import com.github.frosxt.prisoncore.commons.bukkit.item.BukkitItemBuilder;
import com.github.frosxt.prisoncore.commons.bukkit.item.PersistentDataAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class WithdrawNoteService {
    private static final String KEY_MARKER = "economy-note-marker";
    private static final String KEY_CURRENCY = "economy-note-currency";
    private static final String KEY_AMOUNT = "economy-note-amount";
    private static final String KEY_ISSUER = "economy-note-issuer";
    private static final String KEY_ISSUED_AT = "economy-note-issued-at";
    private static final String KEY_VERSION = "economy-note-version";

    private static final int NOTE_VERSION = 1;

    private final Logger logger;

    public WithdrawNoteService(final Logger logger) {
        this.logger = logger;
    }

    public WithdrawNoteIssueResult issue(final WithdrawNoteRequest request, final CurrencyDefinition definition, final BalanceSnapshot newBalance) {
        final WithdrawItemTemplate template = definition.withdrawItem();
        final Material material = parseMaterial(template.material());
        if (material == null) {
            return new WithdrawNoteIssueResult.Failed("invalid material: " + template.material());
        }

        final String issuerName = resolveName(request.playerId());
        final String amountText = request.amount().toPlainString();

        final BukkitItemBuilder builder = BukkitItemBuilder.of(material)
                .name(replaceNoteTokens(template.name(), amountText, issuerName, definition.displayName(), definition.singularName(), definition.pluralName()))
                .lore(template.lore().stream()
                        .map(line -> replaceNoteTokens(line, amountText, issuerName,
                                definition.displayName(), definition.singularName(), definition.pluralName()))
                        .toList())
                .nbtString(KEY_MARKER, "true")
                .nbtString(KEY_CURRENCY, definition.key().value())
                .nbtString(KEY_AMOUNT, amountText)
                .nbtString(KEY_ISSUER, request.playerId().toString())
                .nbtString(KEY_ISSUED_AT, Long.toString(System.currentTimeMillis()))
                .nbtInt(KEY_VERSION, NOTE_VERSION);

        if (template.enchanted()) {
            builder.enchant(Enchantment.DURABILITY).hideFlags();
        }

        return new WithdrawNoteIssueResult.Success(builder.build(), newBalance);
    }

    public WithdrawNoteRedeemResult redeem(final UUID playerId, final ItemStack item, final EconomyService economy) {
        if (item == null || item.getType() == Material.AIR) {
            return new WithdrawNoteRedeemResult.NotAWithdrawNote();
        }
        final ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return new WithdrawNoteRedeemResult.NotAWithdrawNote();
        }
        final PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (PersistentDataAdapter.getString(pdc, KEY_MARKER).isEmpty()) {
            return new WithdrawNoteRedeemResult.NotAWithdrawNote();
        }

        final Optional<String> currencyKeyRaw = PersistentDataAdapter.getString(pdc, KEY_CURRENCY);
        final Optional<String> amountRaw = PersistentDataAdapter.getString(pdc, KEY_AMOUNT);
        if (currencyKeyRaw.isEmpty() || amountRaw.isEmpty()) {
            return new WithdrawNoteRedeemResult.Failed("corrupt withdraw note");
        }

        final CurrencyKey currencyKey;
        try {
            currencyKey = new CurrencyKey(currencyKeyRaw.get());
        } catch (final IllegalArgumentException ex) {
            return new WithdrawNoteRedeemResult.Failed("corrupt currency key in withdraw note");
        }
        if (economy.currency(currencyKey).isEmpty()) {
            return new WithdrawNoteRedeemResult.UnknownCurrency(currencyKey);
        }

        final BigDecimal amount;
        try {
            amount = new BigDecimal(amountRaw.get());
        } catch (final NumberFormatException e) {
            return new WithdrawNoteRedeemResult.Failed("corrupt amount in withdraw note");
        }

        if (amount.signum() <= 0) {
            return new WithdrawNoteRedeemResult.AlreadyConsumed();
        }

        final TransactionResult depositResult = economy.deposit(new TransactionRequest(
                playerId, currencyKey, amount, "withdraw-note-redemption"));
        if (!(depositResult instanceof final TransactionResult.Success success)) {
            return new WithdrawNoteRedeemResult.Failed(depositResult.getClass().getSimpleName());
        }

        return new WithdrawNoteRedeemResult.Success(currencyKey, amount, success.newBalance());
    }

    private Material parseMaterial(final String raw) {
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (final IllegalArgumentException e) {
            logger.warning("[Economy] Invalid material for withdraw item: " + raw);
            return null;
        }
    }

    private String resolveName(final UUID playerId) {
        final OfflinePlayer offline = Bukkit.getOfflinePlayer(playerId);
        final String name = offline.getName();
        return name != null ? name : playerId.toString();
    }

    private String replaceNoteTokens(
            final String input,
            final String amount,
            final String playerName,
            final String displayName,
            final String singular,
            final String plural) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        return input
                .replace("%amount%", amount)
                .replace("%player%", playerName)
                .replace("%currency%", displayName)
                .replace("%currency_singular%", singular)
                .replace("%currency_plural%", plural);
    }
}
