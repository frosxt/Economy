package com.github.frosxt.economy.placeholder;

import com.github.frosxt.economy.api.EconomyService;
import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.leaderboard.LeaderboardEntry;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.currency.CurrencyRegistry;
import com.github.frosxt.prisoncore.placeholder.api.PlaceholderContext;
import com.github.frosxt.prisoncore.placeholder.api.PlaceholderResolver;
import com.github.frosxt.prisoncore.placeholder.api.PlaceholderService;
import com.github.frosxt.prisoncore.placeholder.api.ResolutionResult;

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
            if (registry.find(key).isEmpty()) {
                return new ResolutionResult.Unresolved(token);
            }

            final String operation = parts[1];
            return switch (operation) {
                case "balance" -> resolveBalance(context, key, false);
                case "formatted" -> resolveFormatted(context, key, parts);
                case "rank" -> resolveRank(context, key);
                case "paytoggle" -> resolvePayToggle(context, key);
                case "top" -> resolveTop(key, parts);
                default -> new ResolutionResult.Unresolved(token);
            };
        };
    }

    private ResolutionResult resolveBalance(final PlaceholderContext context, final CurrencyKey key, final boolean formatted) {
        if (context.playerId() == null) {
            return new ResolutionResult.Unresolved("no player in context");
        }
        final String amount = economy.balance(context.playerId(), key).amount().toPlainString();
        return new ResolutionResult.Resolved(amount);
    }

    private ResolutionResult resolveFormatted(final PlaceholderContext context, final CurrencyKey key, final String[] parts) {
        if (parts.length < 3 || !"balance".equals(parts[2])) {
            return new ResolutionResult.Unresolved("expected formatted_balance");
        }
        return resolveBalance(context, key, true);
    }

    private ResolutionResult resolveRank(final PlaceholderContext context, final CurrencyKey key) {
        if (context.playerId() == null) {
            return new ResolutionResult.Unresolved("no player in context");
        }

        final LeaderboardSnapshot snapshot = economy.leaderboard(key);
        final Optional<LeaderboardEntry> entry = snapshot.findByPlayer(context.playerId());
        return entry
                .<ResolutionResult>map(leaderboardEntry ->
                        new ResolutionResult.Resolved(String.valueOf(leaderboardEntry.rank())))
                .orElseGet(() -> new ResolutionResult.Resolved("-"));
    }

    private ResolutionResult resolvePayToggle(final PlaceholderContext context, final CurrencyKey key) {
        if (context.playerId() == null) {
            return new ResolutionResult.Unresolved("no player in context");
        }
        final boolean enabled = economy.paymentToggle(context.playerId(), key).enabled();
        return new ResolutionResult.Resolved(enabled ? "enabled" : "disabled");
    }

    private ResolutionResult resolveTop(final CurrencyKey key, final String[] parts) {
        if (parts.length < 4) {
            return new ResolutionResult.Unresolved("expected top_<n>_name or top_<n>_amount");
        }
        final int rank;
        try {
            rank = Integer.parseInt(parts[2]);
        } catch (final NumberFormatException e) {
            return new ResolutionResult.Unresolved("invalid rank: " + parts[2]);
        }
        final LeaderboardSnapshot snapshot = economy.leaderboard(key);
        if (rank < 1 || rank > snapshot.entries().size()) {
            return new ResolutionResult.Resolved("-");
        }
        final LeaderboardEntry entry = snapshot.entries().get(rank - 1);
        return switch (parts[3]) {
            case "name" -> new ResolutionResult.Resolved(entry.username());
            case "amount" -> new ResolutionResult.Resolved(entry.amount().toPlainString());
            default -> new ResolutionResult.Unresolved("unknown top field: " + parts[3]);
        };
    }
}
