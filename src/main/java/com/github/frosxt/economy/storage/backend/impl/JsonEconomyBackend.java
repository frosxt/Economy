package com.github.frosxt.economy.storage.backend.impl;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.leaderboard.LeaderboardEntry;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.storage.backend.EconomyBackend;
import com.github.frosxt.economy.storage.model.LedgerEntry;
import com.google.gson.*;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class JsonEconomyBackend implements EconomyBackend {
    private static final int LEDGER_TAIL_LINES = 512;

    private final Path root;
    private final Path balancesDir;
    private final Path ledgersDir;
    private final Path togglesFile;
    private final Path leaderboardsDir;
    private final Logger logger;
    private final Gson gson;

    public JsonEconomyBackend(final Path root, final Logger logger) {
        this.root = Objects.requireNonNull(root, "root");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.balancesDir = root.resolve("balances");
        this.ledgersDir = root.resolve("ledgers");
        this.togglesFile = root.resolve("payment-toggles.json");
        this.leaderboardsDir = root.resolve("leaderboards");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            Files.createDirectories(balancesDir);
            Files.createDirectories(ledgersDir);
            Files.createDirectories(leaderboardsDir);
        } catch (final IOException ex) {
            throw new UncheckedIOException("failed to create json backend directories under " + root, ex);
        }
    }

    @Override
    public String name() {
        return "json";
    }

    @Override
    public Map<String, BigDecimal> loadBalances(final UUID playerId) {
        final Map<String, BigDecimal> out = new HashMap<>();
        try {
            if (!Files.isDirectory(balancesDir)) {
                return out;
            }
            try (final var stream = Files.newDirectoryStream(balancesDir, "*.json")) {
                for (final Path file : stream) {
                    final String currency = stripExtension(file.getFileName().toString());
                    final Map<UUID, BigDecimal> all = readCurrencyFile(file);
                    final BigDecimal amount = all.get(playerId);
                    if (amount != null) {
                        out.put(currency, amount);
                    }
                }
            }
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load balances for " + playerId, ex);
        }
        return out;
    }

    @Override
    public void saveBalances(final UUID playerId, final Map<String, BigDecimal> balances) {
        for (final Map.Entry<String, BigDecimal> entry : balances.entrySet()) {
            writeBalanceEntry(entry.getKey(), playerId, entry.getValue());
        }
    }

    @Override
    public Map<UUID, BigDecimal> loadAllForCurrency(final CurrencyKey currency) {
        return readCurrencyFile(balanceFileFor(currency));
    }

    @Override
    public void appendLedgerEntries(final CurrencyKey currency, final List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        final Path file = ledgerFileFor(currency);
        final StringBuilder buffer = new StringBuilder();
        for (final LedgerEntry entry : entries) {
            buffer.append(serializeLedger(entry)).append('\n');
        }
        try {
            Files.writeString(
                    file,
                    buffer.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to append ledger entries for " + currency.value(), ex);
        }
    }

    @Override
    public List<LedgerEntry> loadRecentLedger(final UUID playerId, final CurrencyKey currency, final int limit) {
        final Path file = ledgerFileFor(currency);
        if (!Files.exists(file)) {
            return Collections.emptyList();
        }
        final List<LedgerEntry> tail = new ArrayList<>();
        try {
            final List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            final int startIdx = Math.max(0, lines.size() - LEDGER_TAIL_LINES);
            for (int i = lines.size() - 1; i >= startIdx && tail.size() < limit; i--) {
                final String line = lines.get(i);
                if (line.isBlank()) {
                    continue;
                }
                final LedgerEntry parsed = deserializeLedger(line, currency);
                if (parsed != null && parsed.target().equals(playerId)) {
                    tail.add(parsed);
                }
            }
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load recent ledger for " + currency.value(), ex);
        }
        return tail;
    }

    @Override
    public Map<UUID, Boolean> loadPaymentToggles(final CurrencyKey currency) {
        final Map<UUID, Boolean> out = new HashMap<>();
        if (!Files.exists(togglesFile)) {
            return out;
        }
        try {
            final String content = Files.readString(togglesFile, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return out;
            }
            final JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            final JsonElement section = root.get(currency.value());
            if (section == null || !section.isJsonObject()) {
                return out;
            }
            final JsonObject map = section.getAsJsonObject();
            for (final String key : map.keySet()) {
                try {
                    out.put(UUID.fromString(key), map.get(key).getAsBoolean());
                } catch (final IllegalArgumentException ignored) {
                }
            }
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load payment toggles", ex);
        }
        return out;
    }

    @Override
    public void savePaymentToggle(final UUID playerId, final CurrencyKey currency, final boolean enabled) {
        final JsonObject root;
        try {
            if (Files.exists(togglesFile)) {
                final String content = Files.readString(togglesFile, StandardCharsets.UTF_8);
                root = content.isBlank()
                        ? new JsonObject()
                        : JsonParser.parseString(content).getAsJsonObject();
            } else {
                root = new JsonObject();
            }
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to read toggles file, rewriting", ex);
            writeAtomic(togglesFile, gson.toJson(new JsonObject()));
            return;
        }
        final JsonObject section;
        final JsonElement existing = root.get(currency.value());
        if (existing != null && existing.isJsonObject()) {
            section = existing.getAsJsonObject();
        } else {
            section = new JsonObject();
            root.add(currency.value(), section);
        }
        section.addProperty(playerId.toString(), enabled);
        writeAtomic(togglesFile, gson.toJson(root));
    }

    @Override
    public Optional<LeaderboardSnapshot> loadLeaderboard(final CurrencyKey currency) {
        final Path file = leaderboardFileFor(currency);
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return Optional.empty();
            }
            final JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            final List<LeaderboardEntry> entries = new ArrayList<>();
            final JsonArray arr = root.getAsJsonArray("entries");
            for (final JsonElement element : arr) {
                final JsonObject obj = element.getAsJsonObject();
                entries.add(new LeaderboardEntry(
                        obj.get("rank").getAsInt(),
                        UUID.fromString(obj.get("playerId").getAsString()),
                        obj.get("username").getAsString(),
                        new BigDecimal(obj.get("amount").getAsString())));
            }
            final Instant generatedAt = Instant.parse(root.get("generatedAt").getAsString());
            final long durationMs = root.has("generationDurationMs") ? root.get("generationDurationMs").getAsLong() : 0L;
            return Optional.of(new LeaderboardSnapshot(currency, entries, generatedAt, Duration.ofMillis(durationMs)));
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load leaderboard for " + currency.value(), ex);
            return Optional.empty();
        }
    }

    @Override
    public void saveLeaderboard(final LeaderboardSnapshot snapshot) {
        final JsonObject root = new JsonObject();
        root.addProperty("currency", snapshot.currency().value());
        root.addProperty("generatedAt", snapshot.generatedAt().toString());
        root.addProperty("generationDurationMs", snapshot.generationDuration().toMillis());
        final JsonArray arr = new JsonArray();
        for (final LeaderboardEntry entry : snapshot.entries()) {
            final JsonObject obj = new JsonObject();
            obj.addProperty("rank", entry.rank());
            obj.addProperty("playerId", entry.playerId().toString());
            obj.addProperty("username", entry.username());
            obj.addProperty("amount", entry.amount().toPlainString());
            arr.add(obj);
        }
        root.add("entries", arr);
        writeAtomic(leaderboardFileFor(snapshot.currency()), gson.toJson(root));
    }

    @Override
    public void close() {
        logger.info("[Economy] Json backend closed (root: " + root + ")");
    }

    private void writeBalanceEntry(final String currency, final UUID playerId, final BigDecimal amount) {
        final Path file = balancesDir.resolve(currency + ".json");
        final Map<UUID, BigDecimal> all = readCurrencyFile(file);
        all.put(playerId, amount);
        final JsonObject root = new JsonObject();
        for (final Map.Entry<UUID, BigDecimal> entry : all.entrySet()) {
            root.addProperty(entry.getKey().toString(), entry.getValue().toPlainString());
        }
        writeAtomic(file, gson.toJson(root));
    }

    private Map<UUID, BigDecimal> readCurrencyFile(final Path file) {
        final Map<UUID, BigDecimal> out = new HashMap<>();
        if (!Files.exists(file)) {
            return out;
        }
        try {
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            if (content.isBlank()) {
                return out;
            }
            final JsonObject root = JsonParser.parseString(content).getAsJsonObject();
            for (final String key : root.keySet()) {
                try {
                    out.put(UUID.fromString(key), new BigDecimal(root.get(key).getAsString()));
                } catch (final IllegalArgumentException ignored) {
                }
            }
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to read balance file " + file, ex);
        }
        return out;
    }

    private Path balanceFileFor(final CurrencyKey currency) {
        return safeChild(balancesDir, currency.value() + ".json");
    }

    private Path ledgerFileFor(final CurrencyKey currency) {
        return safeChild(ledgersDir, currency.value() + ".jsonl");
    }

    private Path leaderboardFileFor(final CurrencyKey currency) {
        return safeChild(leaderboardsDir, currency.value() + ".json");
    }

    private static Path safeChild(final Path parent, final String child) {
        final Path resolved = parent.resolve(child).normalize();
        if (!resolved.startsWith(parent.normalize())) {
            throw new IllegalArgumentException("refusing path traversal outside " + parent + ": " + child);
        }
        return resolved;
    }

    private void writeAtomic(final Path file, final String content) {
        final Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (final AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (final IOException ex) {
            logger.log(Level.WARNING, "[Economy] failed to write file " + file, ex);
        }
    }

    private String serializeLedger(final LedgerEntry entry) {
        final JsonObject obj = new JsonObject();
        obj.addProperty("id", entry.id());
        obj.addProperty("timestamp", entry.timestamp().toString());
        obj.addProperty("actor", entry.actor() != null ? entry.actor().toString() : "");
        obj.addProperty("target", entry.target().toString());
        obj.addProperty("amount", entry.amount().toPlainString());
        obj.addProperty("operation", entry.operation());
        obj.addProperty("reason", entry.reason() != null ? entry.reason() : "");
        obj.addProperty("preBalance", entry.preBalance().toPlainString());
        obj.addProperty("postBalance", entry.postBalance().toPlainString());
        return gson.toJson(obj);
    }

    private LedgerEntry deserializeLedger(final String line, final CurrencyKey currency) {
        try {
            final JsonObject obj = JsonParser.parseString(line).getAsJsonObject();
            final String actorStr = obj.get("actor").getAsString();
            return new LedgerEntry(
                    obj.get("id").getAsString(),
                    Instant.parse(obj.get("timestamp").getAsString()),
                    currency,
                    actorStr.isEmpty() ? null : UUID.fromString(actorStr),
                    UUID.fromString(obj.get("target").getAsString()),
                    new BigDecimal(obj.get("amount").getAsString()),
                    obj.get("operation").getAsString(),
                    obj.get("reason").getAsString(),
                    new BigDecimal(obj.get("preBalance").getAsString()),
                    new BigDecimal(obj.get("postBalance").getAsString()));
        } catch (final RuntimeException ex) {
            return null;
        }
    }

    private static String stripExtension(final String name) {
        final int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
