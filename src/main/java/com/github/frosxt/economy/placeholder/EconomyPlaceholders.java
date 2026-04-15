package com.github.frosxt.economy.placeholder;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.currency.NumberFormatPolicy;
import com.github.frosxt.economy.api.leaderboard.LeaderboardEntry;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.currency.CurrencyRegistry;
import com.github.frosxt.economy.format.BalanceFormatter;
import com.github.frosxt.prisoncore.placeholder.api.PlaceholderContext;
import com.github.frosxt.prisoncore.placeholder.api.PlaceholderResolver;
import com.github.frosxt.prisoncore.placeholder.api.PlaceholderService;
import com.github.frosxt.prisoncore.placeholder.api.ResolutionResult;

import java.math.BigDecimal;
import java.util.Optional;

public final class EconomyPlaceholders {
    private static final String NAMESPACE = "economy";

    private final EconomyService economy;
    private final CurrencyRegistry registry;

    public EconomyPlaceholders(final EconomyService economy, final CurrencyRegistry registry) {
        this.economy = economy;
        this.registry = registry;
    }

    public void register(final PlaceholderService placeholderService) {
        placeholderService.registerResolver(NAMESPACE, buildResolver());
    }

    public void unregister(final PlaceholderService placeholderService) {
        placeholderService.unregisterResolver(NAMESPACE);
    }

    private PlaceholderResolver buildResolver() {
        return (context, token) -> {
            final String[] parts = token.split("_");
            if (parts.length < 2) {
                return new ResolutionResult.Unresolved(token);
            }

            final CurrencyKey key;
            try {
                key = new CurrencyKey(parts[0]);
            } catch (final IllegalArgumentException ex) {
                return new ResolutionResult.Unresolved(token);
            }
            final Optional<CurrencyDefinition> definitionOpt = registry.find(key);
            if (definitionOpt.isEmpty()) {
                return new ResolutionResult.Unresolved(token);
            }
            final CurrencyDefinition definition = definitionOpt.get();

            final String operation = parts[1];
            return switch (operation) {
                case "balance" -> resolveBalance(context, definition, BalanceStyle.PLAIN);
                case "formatted" -> resolveBalanceWithStyle(context, definition, parts, BalanceStyle.POLICY);
                case "short" -> resolveBalanceWithStyle(context, definition, parts, BalanceStyle.SHORT);
                case "long" -> resolveBalanceWithStyle(context, definition, parts, BalanceStyle.LONG);
                case "rank" -> resolveRank(context, definition);
                case "paytoggle" -> resolvePayToggle(context, definition);
                case "top" -> resolveTop(definition, parts);
                default -> new ResolutionResult.Unresolved(token);
            };
        };
    }

    private ResolutionResult resolveBalanceWithStyle(
            final PlaceholderContext context,
            final CurrencyDefinition definition,
            final String[] parts,
            final BalanceStyle style) {
        if (parts.length < 3 || !"balance".equals(parts[2])) {
            return new ResolutionResult.Unresolved("expected " + parts[1] + "_balance");
        }
        return resolveBalance(context, definition, style);
    }

    private ResolutionResult resolveBalance(
            final PlaceholderContext context,
            final CurrencyDefinition definition,
            final BalanceStyle style) {
        if (context.playerId() == null) {
            return new ResolutionResult.Unresolved("no player in context");
        }
        final BigDecimal amount = economy.balance(context.playerId(), definition.key()).amount();
        return new ResolutionResult.Resolved(format(amount, definition.numberFormat(), style));
    }

    private ResolutionResult resolveRank(final PlaceholderContext context, final CurrencyDefinition definition) {
        if (context.playerId() == null) {
            return new ResolutionResult.Unresolved("no player in context");
        }

        final LeaderboardSnapshot snapshot = economy.leaderboard(definition.key());
        final Optional<LeaderboardEntry> entry = snapshot.findByPlayer(context.playerId());
        return entry
                .<ResolutionResult>map(leaderboardEntry ->
                        new ResolutionResult.Resolved(String.valueOf(leaderboardEntry.rank())))
                .orElseGet(() -> new ResolutionResult.Resolved("-"));
    }

    private ResolutionResult resolvePayToggle(final PlaceholderContext context, final CurrencyDefinition definition) {
        if (context.playerId() == null) {
            return new ResolutionResult.Unresolved("no player in context");
        }
        final boolean enabled = economy.paymentToggle(context.playerId(), definition.key()).enabled();
        return new ResolutionResult.Resolved(enabled ? "enabled" : "disabled");
    }

    private ResolutionResult resolveTop(final CurrencyDefinition definition, final String[] parts) {
        if (parts.length < 4) {
            return new ResolutionResult.Unresolved("expected top_<n>_name or top_<n>_amount[_style]");
        }
        final int rank;
        try {
            rank = Integer.parseInt(parts[2]);
        } catch (final NumberFormatException e) {
            return new ResolutionResult.Unresolved("invalid rank: " + parts[2]);
        }
        final LeaderboardSnapshot snapshot = economy.leaderboard(definition.key());
        if (rank < 1 || rank > snapshot.entries().size()) {
            return new ResolutionResult.Resolved("-");
        }
        final LeaderboardEntry entry = snapshot.entries().get(rank - 1);
        return switch (parts[3]) {
            case "name" -> new ResolutionResult.Resolved(entry.username());
            case "amount" -> resolveTopAmount(definition, entry, parts);
            default -> new ResolutionResult.Unresolved("unknown top field: " + parts[3]);
        };
    }

    private ResolutionResult resolveTopAmount(
            final CurrencyDefinition definition,
            final LeaderboardEntry entry,
            final String[] parts) {
        final BalanceStyle style;
        if (parts.length == 4) {
            style = BalanceStyle.PLAIN;
        } else {
            style = switch (parts[4]) {
                case "formatted" -> BalanceStyle.POLICY;
                case "short" -> BalanceStyle.SHORT;
                case "long" -> BalanceStyle.LONG;
                case "plain" -> BalanceStyle.PLAIN;
                default -> null;
            };
            if (style == null) {
                return new ResolutionResult.Unresolved("unknown amount style: " + parts[4]);
            }
        }
        return new ResolutionResult.Resolved(format(entry.amount(), definition.numberFormat(), style));
    }

    private static String format(
            final BigDecimal amount,
            final NumberFormatPolicy policy,
            final BalanceStyle style) {
        return switch (style) {
            case PLAIN -> BalanceFormatter.plain(amount);
            case POLICY -> BalanceFormatter.format(amount, policy);
            case SHORT -> BalanceFormatter.shortSuffix(amount, policy);
            case LONG -> BalanceFormatter.grouped(amount, policy);
        };
    }

    private enum BalanceStyle {
        PLAIN,
        POLICY,
        SHORT,
        LONG
    }
}
