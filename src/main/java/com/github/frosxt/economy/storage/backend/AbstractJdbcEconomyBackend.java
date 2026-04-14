package com.github.frosxt.economy.storage.backend;

import com.github.frosxt.economy.api.currency.CurrencyKey;
import com.github.frosxt.economy.api.leaderboard.LeaderboardEntry;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.storage.model.LedgerEntry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractJdbcEconomyBackend implements EconomyBackend {
    protected final Logger logger;
    protected final Gson gson = new Gson();
    protected AbstractJdbcEconomyBackend(final Logger logger) {
        this.logger = logger;
    }
    protected abstract Connection connection() throws SQLException;
    protected abstract boolean owningConnection();

    protected void releaseConnection(final Connection connection) {
        if (!owningConnection() && connection != null) {
            try {
                connection.close();
            } catch (final SQLException ex) {
                logger.log(Level.WARNING, "[Economy] failed to release pooled connection", ex);
            }
        }
    }

    protected void createSchema() {
        try (final Connection conn = connection();
             final Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS economy_balances ("
                            + "player_id VARCHAR(36) NOT NULL,"
                            + "currency VARCHAR(64) NOT NULL,"
                            + "amount VARCHAR(64) NOT NULL,"
                            + "PRIMARY KEY (player_id, currency))");
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS economy_ledger ("
                            + "id VARCHAR(36) NOT NULL PRIMARY KEY,"
                            + "ts VARCHAR(40) NOT NULL,"
                            + "currency VARCHAR(64) NOT NULL,"
                            + "actor VARCHAR(36),"
                            + "target VARCHAR(36) NOT NULL,"
                            + "amount VARCHAR(64) NOT NULL,"
                            + "operation VARCHAR(16) NOT NULL,"
                            + "reason VARCHAR(255),"
                            + "pre_balance VARCHAR(64) NOT NULL,"
                            + "post_balance VARCHAR(64) NOT NULL)");
            stmt.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS economy_ledger_target_currency ON economy_ledger(target, currency)");
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS economy_payment_toggles ("
                            + "player_id VARCHAR(36) NOT NULL,"
                            + "currency VARCHAR(64) NOT NULL,"
                            + "enabled INTEGER NOT NULL,"
                            + "PRIMARY KEY (player_id, currency))");
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS economy_leaderboard ("
                            + "currency VARCHAR(64) NOT NULL PRIMARY KEY,"
                            + "snapshot_json TEXT NOT NULL,"
                            + "updated_at VARCHAR(40) NOT NULL)");
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (final SQLException ex) {
            throw new IllegalStateException("[Economy] failed to create JDBC schema", ex);
        }
    }

    @Override
    public Map<String, BigDecimal> loadBalances(final UUID playerId) {
        final Map<String, BigDecimal> out = new HashMap<>();
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement("SELECT currency, amount FROM economy_balances WHERE player_id = ?")) {
            ps.setString(1, playerId.toString());
            try (final ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.put(rs.getString(1), new BigDecimal(rs.getString(2)));
                }
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load balances for " + playerId, ex);
        } finally {
            releaseConnection(conn);
        }
        return out;
    }

    @Override
    public void saveBalances(final UUID playerId, final Map<String, BigDecimal> balances) {
        if (balances.isEmpty()) {
            return;
        }
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement(upsertBalanceSql())) {
            for (final Map.Entry<String, BigDecimal> entry : balances.entrySet()) {
                ps.setString(1, playerId.toString());
                ps.setString(2, entry.getKey());
                ps.setString(3, entry.getValue().toPlainString());
                ps.addBatch();
            }
            ps.executeBatch();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to save balances for " + playerId, ex);
        } finally {
            releaseConnection(conn);
        }
    }

    protected abstract String upsertBalanceSql();

    @Override
    public Map<UUID, BigDecimal> loadAllForCurrency(final CurrencyKey currency) {
        final Map<UUID, BigDecimal> out = new HashMap<>();
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT player_id, amount FROM economy_balances WHERE currency = ?")) {
            ps.setString(1, currency.value());
            try (final ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.put(UUID.fromString(rs.getString(1)), new BigDecimal(rs.getString(2)));
                    } catch (final IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load all balances for " + currency.value(), ex);
        } finally {
            releaseConnection(conn);
        }
        return out;
    }

    @Override
    public void appendLedgerEntries(final CurrencyKey currency, final List<LedgerEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO economy_ledger(id, ts, currency, actor, target, amount, operation, reason, pre_balance, post_balance)" + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            for (final LedgerEntry entry : entries) {
                ps.setString(1, entry.id());
                ps.setString(2, entry.timestamp().toString());
                ps.setString(3, currency.value());
                ps.setString(4, entry.actor() != null ? entry.actor().toString() : null);
                ps.setString(5, entry.target().toString());
                ps.setString(6, entry.amount().toPlainString());
                ps.setString(7, entry.operation());
                ps.setString(8, entry.reason());
                ps.setString(9, entry.preBalance().toPlainString());
                ps.setString(10, entry.postBalance().toPlainString());
                ps.addBatch();
            }
            ps.executeBatch();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to append ledger for " + currency.value(), ex);
        } finally {
            releaseConnection(conn);
        }
    }

    @Override
    public List<LedgerEntry> loadRecentLedger(final UUID playerId, final CurrencyKey currency, final int limit) {
        final List<LedgerEntry> out = new ArrayList<>();
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement(
                "SELECT id, ts, actor, target, amount, operation, reason, pre_balance, post_balance" + " FROM economy_ledger WHERE target = ? AND currency = ? ORDER BY ts DESC LIMIT ?")) {
            ps.setString(1, playerId.toString());
            ps.setString(2, currency.value());
            ps.setInt(3, limit);
            try (final ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    final String actorStr = rs.getString(3);
                    out.add(new LedgerEntry(
                            rs.getString(1),
                            Instant.parse(rs.getString(2)),
                            currency,
                            actorStr != null ? UUID.fromString(actorStr) : null,
                            UUID.fromString(rs.getString(4)),
                            new BigDecimal(rs.getString(5)),
                            rs.getString(6),
                            rs.getString(7),
                            new BigDecimal(rs.getString(8)),
                            new BigDecimal(rs.getString(9))));
                }
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load recent ledger for " + currency.value(), ex);
        } finally {
            releaseConnection(conn);
        }
        return out;
    }

    @Override
    public Map<UUID, Boolean> loadPaymentToggles(final CurrencyKey currency) {
        final Map<UUID, Boolean> out = new HashMap<>();
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement("SELECT player_id, enabled FROM economy_payment_toggles WHERE currency = ?")) {
            ps.setString(1, currency.value());
            try (final ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.put(UUID.fromString(rs.getString(1)), rs.getInt(2) != 0);
                    } catch (final IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load payment toggles for " + currency.value(), ex);
        } finally {
            releaseConnection(conn);
        }
        return out;
    }

    @Override
    public void savePaymentToggle(final UUID playerId, final CurrencyKey currency, final boolean enabled) {
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement(upsertToggleSql())) {
            ps.setString(1, playerId.toString());
            ps.setString(2, currency.value());
            ps.setInt(3, enabled ? 1 : 0);
            ps.executeUpdate();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to save payment toggle", ex);
        } finally {
            releaseConnection(conn);
        }
    }

    protected abstract String upsertToggleSql();

    @Override
    public Optional<LeaderboardSnapshot> loadLeaderboard(final CurrencyKey currency) {
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement("SELECT snapshot_json, updated_at FROM economy_leaderboard WHERE currency = ?")) {
            ps.setString(1, currency.value());
            try (final ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(deserializeLeaderboard(currency, rs.getString(1), rs.getString(2)));
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to load leaderboard for " + currency.value(), ex);
            return Optional.empty();
        } finally {
            releaseConnection(conn);
        }
    }

    @Override
    public void saveLeaderboard(final LeaderboardSnapshot snapshot) {
        final Connection conn = openConnection();
        try (final PreparedStatement ps = conn.prepareStatement(upsertLeaderboardSql())) {
            ps.setString(1, snapshot.currency().value());
            ps.setString(2, serializeLeaderboard(snapshot));
            ps.setString(3, snapshot.generatedAt().toString());
            ps.executeUpdate();
            if (!conn.getAutoCommit()) {
                conn.commit();
            }
        } catch (final SQLException ex) {
            logger.log(Level.WARNING, "[Economy] failed to save leaderboard for " + snapshot.currency().value(), ex);
        } finally {
            releaseConnection(conn);
        }
    }

    protected abstract String upsertLeaderboardSql();

    private Connection openConnection() {
        try {
            return connection();
        } catch (final SQLException ex) {
            throw new IllegalStateException("[Economy] failed to open JDBC connection", ex);
        }
    }

    private String serializeLeaderboard(final LeaderboardSnapshot snapshot) {
        final JsonObject root = new JsonObject();
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
        return gson.toJson(root);
    }

    private LeaderboardSnapshot deserializeLeaderboard(final CurrencyKey currency, final String json, final String updatedAt) {
        try {
            final JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            final long durationMs = root.has("generationDurationMs") ? root.get("generationDurationMs").getAsLong() : 0L;
            final List<LeaderboardEntry> entries = new ArrayList<>();
            final JsonArray arr = root.getAsJsonArray("entries");
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    final JsonObject obj = arr.get(i).getAsJsonObject();
                    entries.add(new LeaderboardEntry(
                            obj.get("rank").getAsInt(),
                            UUID.fromString(obj.get("playerId").getAsString()),
                            obj.get("username").getAsString(),
                            new BigDecimal(obj.get("amount").getAsString())));
                }
            }
            return new LeaderboardSnapshot(currency, entries, Instant.parse(updatedAt), Duration.ofMillis(durationMs));
        } catch (final RuntimeException ex) {
            logger.log(Level.WARNING, "[Economy] failed to parse leaderboard json for " + currency.value(), ex);
            return new LeaderboardSnapshot(currency, Collections.emptyList(), Instant.now(), Duration.ZERO);
        }
    }
}
