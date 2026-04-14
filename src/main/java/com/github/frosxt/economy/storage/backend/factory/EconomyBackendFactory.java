package com.github.frosxt.economy.storage.backend.factory;

import com.github.frosxt.economy.storage.backend.EconomyBackend;
import com.github.frosxt.economy.storage.backend.impl.JsonEconomyBackend;
import com.github.frosxt.economy.storage.backend.impl.MongoEconomyBackend;
import com.github.frosxt.economy.storage.backend.impl.SqlEconomyBackend;
import com.github.frosxt.economy.storage.backend.impl.SqliteEconomyBackend;
import com.github.frosxt.prisoncore.kernel.config.CoreConfig;
import com.github.frosxt.prisoncore.kernel.storage.StorageRegistry;
import com.github.frosxt.prisoncore.spi.storage.StorageBackend;
import com.github.frosxt.prisoncore.storage.json.JsonStorageBackend;
import com.github.frosxt.prisoncore.storage.mongo.MongoBackend;
import com.github.frosxt.prisoncore.storage.sql.SqlBackend;
import com.github.frosxt.prisoncore.storage.sqlite.SqliteBackend;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

public final class EconomyBackendFactory {
    private EconomyBackendFactory() {
        throw new UnsupportedOperationException("Utility classes cannot be instantiated");
    }

    public static EconomyBackend create(final CoreConfig coreConfig, final Path dataRoot, final StorageRegistry registry, final Logger logger) {
        final String normalized = coreConfig.storageBackend() == null ? "json" : coreConfig.storageBackend().toLowerCase(Locale.ROOT);
        final Map<String, Object> coreSettings = new HashMap<>(coreConfig.storageProperties());

        return switch (normalized) {
            case "json" -> buildJson(dataRoot, registry, logger);
            case "sqlite" -> buildSqlite(dataRoot, registry, logger);
            case "sql", "mysql", "mariadb" -> buildSql(coreSettings, registry, logger);
            case "mongo", "mongodb" -> buildMongo(coreSettings, registry, logger);
            default -> {
                logger.warning("[Economy] Unknown storage backend '" + coreConfig.storageBackend()
                        + "' from core.yml, falling back to json.");
                yield buildJson(dataRoot, registry, logger);
            }
        };
    }

    private static EconomyBackend buildJson(final Path dataRoot, final StorageRegistry registry, final Logger logger) {
        final String path = dataRoot.resolve("data").toString();
        final StorageBackend backend = registry.getOrCreate("json", "economy", Map.of("directory", path));
        if (!(backend instanceof final JsonStorageBackend json)) {
            throw new IllegalStateException("Expected JsonStorageBackend from StorageRegistry, got " + backend.getClass());
        }

        return new JsonEconomyBackend(json.directory(), logger);
    }

    private static EconomyBackend buildSqlite(final Path dataRoot, final StorageRegistry registry, final Logger logger) {
        final Path dbFile = dataRoot.resolve("data").resolve("economy.db");
        final StorageBackend backend = registry.getOrCreate("sqlite", "economy", Map.of("file", dbFile.toString()));
        if (!(backend instanceof final SqliteBackend sqlite)) {
            throw new IllegalStateException("Expected SqliteBackend from StorageRegistry, got " + backend.getClass());
        }
        return new SqliteEconomyBackend(sqlite, logger);
    }

    private static EconomyBackend buildSql(final Map<String, Object> settings, final StorageRegistry registry, final Logger logger) {
        final StorageBackend backend = registry.getOrCreate("sql", "economy", settings);
        if (!(backend instanceof final SqlBackend sql)) {
            throw new IllegalStateException("Expected SqlBackend from StorageRegistry, got " + backend.getClass());
        }
        return new SqlEconomyBackend(sql, logger);
    }

    private static EconomyBackend buildMongo(final Map<String, Object> settings, final StorageRegistry registry, final Logger logger) {
        final StorageBackend backend = registry.getOrCreate("mongo", "economy", settings);
        if (!(backend instanceof final MongoBackend mongo)) {
            throw new IllegalStateException("Expected MongoBackend from StorageRegistry, got " + backend.getClass());
        }
        return new MongoEconomyBackend(mongo.database(), logger);
    }
}
