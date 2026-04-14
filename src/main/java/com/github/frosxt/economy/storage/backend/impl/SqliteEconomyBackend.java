package com.github.frosxt.economy.storage.backend.impl;

import com.github.frosxt.economy.storage.backend.AbstractJdbcEconomyBackend;
import com.github.frosxt.prisoncore.storage.sqlite.SqliteBackend;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class SqliteEconomyBackend extends AbstractJdbcEconomyBackend {
    private final SqliteBackend backend;

    public SqliteEconomyBackend(final SqliteBackend backend, final Logger logger) {
        super(logger);
        this.backend = backend;
        createSchema();
    }

    @Override
    public String name() {
        return "sqlite";
    }

    @Override
    protected Connection connection() throws SQLException {
        return backend.connection();
    }

    @Override
    protected boolean owningConnection() {
        return true;
    }

    @Override
    protected String upsertBalanceSql() {
        return "INSERT INTO economy_balances(player_id, currency, amount) VALUES (?, ?, ?)" + " ON CONFLICT(player_id, currency) DO UPDATE SET amount = excluded.amount";
    }

    @Override
    protected String upsertToggleSql() {
        return "INSERT INTO economy_payment_toggles(player_id, currency, enabled) VALUES (?, ?, ?)" + " ON CONFLICT(player_id, currency) DO UPDATE SET enabled = excluded.enabled";
    }

    @Override
    protected String upsertLeaderboardSql() {
        return "INSERT INTO economy_leaderboard(currency, snapshot_json, updated_at) VALUES (?, ?, ?)" + " ON CONFLICT(currency) DO UPDATE SET snapshot_json = excluded.snapshot_json, updated_at = excluded.updated_at";
    }

    @Override
    public void close() {
        logger.info("[Economy] SQLite backend released (lifecycle owned by core StorageRegistry).");
    }
}
