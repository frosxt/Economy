package com.github.frosxt.economy.storage.backend.impl;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.leaderboard.LeaderboardEntry;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.storage.backend.EconomyBackend;
import com.github.frosxt.economy.storage.model.LedgerEntry;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.*;
import org.bson.Document;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MongoEconomyBackend implements EconomyBackend {
    private final MongoDatabase database;
    private final Logger logger;
    private final MongoCollection<Document> balances;
    private final MongoCollection<Document> ledger;
    private final MongoCollection<Document> toggles;
    private final MongoCollection<Document> leaderboard;

    public MongoEconomyBackend(final MongoDatabase database, final Logger logger) {
        this.database = Objects.requireNonNull(database, "database");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.balances = database.getCollection("economy_balances");
        this.ledger = database.getCollection("economy_ledger");
        this.toggles = database.getCollection("economy_payment_toggles");
        this.leaderboard = database.getCollection("economy_leaderboard");
    }

    @Override
    public String name() {
        return "mongo";
    }

    @Override
    public Map<String, BigDecimal> loadBalances(final UUID playerId) {
        final Map<String, BigDecimal> out = new HashMap<>();
        try {
            final Document doc = balances.find(Filters.eq("_id", playerId.toString())).first();
            if (doc != null) {
                final Document currencies = doc.get("currencies", Document.class);
                if (currencies != null) {
                    for (final String key : currencies.keySet()) {
                        out.put(key, new BigDecimal(currencies.getString(key)));
                    }
                }
            }
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load balances for " + playerId, ex);
        }
        return out;
    }

    @Override
    public void saveBalances(final UUID playerId, final Map<String, BigDecimal> balancesMap) {
        if (balancesMap.isEmpty()) {
            return;
        }
        try {
            final Document currencies = new Document();
            for (final Map.Entry<String, BigDecimal> entry : balancesMap.entrySet()) {
                currencies.append(entry.getKey(), entry.getValue().toPlainString());
            }
            balances.updateOne(
                    Filters.eq("_id", playerId.toString()),
                    Updates.set("currencies", currencies),
                    new UpdateOptions().upsert(true));
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to save balances for " + playerId, ex);
        }
    }

    @Override
    public Map<UUID, BigDecimal> loadAllForCurrency(final CurrencyKey currency) {
        final Map<UUID, BigDecimal> out = new HashMap<>();
        try {
            for (final Document doc : balances.find()) {
                final String id = doc.getString("_id");
                final Document currencies = doc.get("currencies", Document.class);
                if (currencies == null || !currencies.containsKey(currency.value())) {
                    continue;
                }
                try {
                    out.put(UUID.fromString(id), new BigDecimal(currencies.getString(currency.value())));
                } catch (final IllegalArgumentException ignored) {
                }
            }
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to scan balances for " + currency.value(), ex);
        }
        return out;
    }

    @Override
    public void appendLedgerEntries(final CurrencyKey currency, final List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        try {
            final List<Document> docs = new ArrayList<>(entries.size());
            for (final LedgerEntry entry : entries) {
                docs.add(new Document()
                        .append("_id", entry.id())
                        .append("ts", entry.timestamp().toString())
                        .append("currency", currency.value())
                        .append("actor", entry.actor() != null ? entry.actor().toString() : null)
                        .append("target", entry.target().toString())
                        .append("amount", entry.amount().toPlainString())
                        .append("operation", entry.operation())
                        .append("reason", entry.reason())
                        .append("preBalance", entry.preBalance().toPlainString())
                        .append("postBalance", entry.postBalance().toPlainString()));
            }
            ledger.insertMany(docs);
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to append ledger for " + currency.value(), ex);
        }
    }

    @Override
    public List<LedgerEntry> loadRecentLedger(final UUID playerId, final CurrencyKey currency, final int limit) {
        final List<LedgerEntry> out = new ArrayList<>();
        try {
            for (final Document doc : ledger.find(Filters.and(
                            Filters.eq("target", playerId.toString()),
                            Filters.eq("currency", currency.value())))
                    .sort(Sorts.descending("ts"))
                    .limit(limit)) {
                final String actorStr = doc.getString("actor");
                out.add(new LedgerEntry(
                        doc.getString("_id"),
                        Instant.parse(doc.getString("ts")),
                        currency,
                        actorStr != null ? UUID.fromString(actorStr) : null,
                        UUID.fromString(doc.getString("target")),
                        new BigDecimal(doc.getString("amount")),
                        doc.getString("operation"),
                        doc.getString("reason"),
                        new BigDecimal(doc.getString("preBalance")),
                        new BigDecimal(doc.getString("postBalance"))));
            }
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load recent ledger for " + currency.value(), ex);
        }
        return out;
    }

    @Override
    public Map<UUID, Boolean> loadPaymentToggles(final CurrencyKey currency) {
        final Map<UUID, Boolean> out = new HashMap<>();
        try {
            for (final Document doc : toggles.find(Filters.eq("currency", currency.value()))) {
                try {
                    out.put(UUID.fromString(doc.getString("player_id")), doc.getBoolean("enabled", true));
                } catch (final IllegalArgumentException ignored) {
                }
            }
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load payment toggles for " + currency.value(), ex);
        }
        return out;
    }

    @Override
    public void savePaymentToggle(final UUID playerId, final CurrencyKey currency, final boolean enabled) {
        try {
            toggles.updateOne(
                    Filters.and(
                            Filters.eq("player_id", playerId.toString()),
                            Filters.eq("currency", currency.value())),
                    Updates.combine(
                            Updates.set("player_id", playerId.toString()),
                            Updates.set("currency", currency.value()),
                            Updates.set("enabled", enabled)),
                    new UpdateOptions().upsert(true));
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to save payment toggle", ex);
        }
    }

    @Override
    public Optional<LeaderboardSnapshot> loadLeaderboard(final CurrencyKey currency) {
        try {
            final Document doc = leaderboard.find(Filters.eq("_id", currency.value())).first();
            if (doc == null) {
                return Optional.empty();
            }
            final List<LeaderboardEntry> entries = new ArrayList<>();
            final List<Document> rawEntries = doc.getList("entries", Document.class, Collections.emptyList());
            for (final Document entry : rawEntries) {
                entries.add(new LeaderboardEntry(
                        entry.getInteger("rank"),
                        UUID.fromString(entry.getString("playerId")),
                        entry.getString("username"),
                        new BigDecimal(entry.getString("amount"))));
            }
            final Instant generatedAt = Instant.parse(doc.getString("generatedAt"));
            final long durationMs = doc.getLong("durationMs") != null ? doc.getLong("durationMs") : 0L;
            return Optional.of(new LeaderboardSnapshot(currency, entries, generatedAt, Duration.ofMillis(durationMs)));
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load leaderboard for " + currency.value(), ex);
            return Optional.empty();
        }
    }

    @Override
    public void saveLeaderboard(final LeaderboardSnapshot snapshot) {
        try {
            final List<Document> entryDocs = new ArrayList<>();
            for (final LeaderboardEntry entry : snapshot.entries()) {
                entryDocs.add(new Document()
                        .append("rank", entry.rank())
                        .append("playerId", entry.playerId().toString())
                        .append("username", entry.username())
                        .append("amount", entry.amount().toPlainString()));
            }
            final Document doc = new Document()
                    .append("_id", snapshot.currency().value())
                    .append("generatedAt", snapshot.generatedAt().toString())
                    .append("durationMs", snapshot.generationDuration().toMillis())
                    .append("entries", entryDocs);
            leaderboard.replaceOne(Filters.eq("_id", snapshot.currency().value()), doc, new ReplaceOptions().upsert(true));
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to save leaderboard for " + snapshot.currency().value(), ex);
        }
    }

    @Override
    public void close() {
        logger.info("[Economy] Mongo backend released (lifecycle owned by core StorageRegistry).");
    }
}
