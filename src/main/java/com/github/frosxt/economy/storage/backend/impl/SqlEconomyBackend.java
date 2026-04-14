package com.github.frosxt.economy.storage.backend.impl;

import com.github.frosxt.economy.storage.backend.AbstractJdbcEconomyBackend;
import com.github.frosxt.prisoncore.storage.sql.SqlBackend;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Logger;

public final class SqlEconomyBackend extends AbstractJdbcEconomyBackend {
    private final SqlBackend backend;

    public SqlEconomyBackend(final SqlBackend backend, final Logger logger) {
        super(logger);
        this.backend = backend;
        createSchema();
    }

    @Override
    public String name() {
        return "sql";
    }

    @Override
    protected Connection connection() throws SQLException {
        return backend.connection();
    }

    @Override
    protected boolean owningConnection() {
        return false;
    }

    @Override
    protected String upsertBalanceSql() {
        return "INSERT INTO economy_balances(player_id, currency, amount) VALUES (?, ?, ?)" + " ON DUPLICATE KEY UPDATE amount = VALUES(amount)";
    }

    @Override
    protected String upsertToggleSql() {
        return "INSERT INTO economy_payment_toggles(player_id, currency, enabled) VALUES (?, ?, ?)" + " ON DUPLICATE KEY UPDATE enabled = VALUES(enabled)";
    }

    @Override
    protected String upsertLeaderboardSql() {
        return "INSERT INTO economy_leaderboard(currency, snapshot_json, updated_at) VALUES (?, ?, ?)" + " ON DUPLICATE KEY UPDATE snapshot_json = VALUES(snapshot_json), updated_at = VALUES(updated_at)";
    }

    @Override
    public void close() {
        logger.info("[Economy] SQL backend released (pool lifecycle owned by core StorageRegistry).");
    }
}
