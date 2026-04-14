package com.github.frosxt.economy.bootstrap.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public record EconomyConfig(
        boolean rebuildOnEnable,
        int maxEntries,
        long recalcIntervalSeconds,
        long flushIntervalMillis,
        boolean vaultEnabled
) {

    public static EconomyConfig defaults() {
        return new EconomyConfig(true, 100, 300L, 2000L, true);
    }

    public static EconomyConfig load(final Path configFile, final Logger logger) {
        if (!Files.exists(configFile)) {
            return defaults();
        }
        try {
            final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(configFile.toFile());
            final boolean rebuildOnEnable = yaml.getBoolean("leaderboard.rebuild-on-enable", true);
            final int maxEntries = yaml.getInt("leaderboard.max-entries", 100);
            final long recalcIntervalSeconds = yaml.getLong("leaderboard.recalc-interval-seconds", 300L);
            final long flushIntervalMillis = yaml.getLong("storage.flush-interval-millis", 2000L);
            final boolean vaultEnabled = yaml.getBoolean("vault.enabled", true);

            return new EconomyConfig(rebuildOnEnable, maxEntries, recalcIntervalSeconds, flushIntervalMillis, vaultEnabled);
        } catch (final Exception ex) {
            logger.log(Level.WARNING, "[Economy] Failed to load config.yml, using defaults", ex);
            return defaults();
        }
    }
}
