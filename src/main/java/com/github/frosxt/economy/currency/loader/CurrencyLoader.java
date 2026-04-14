package com.github.frosxt.economy.currency.loader;

import com.github.frosxt.economy.api.banknote.WithdrawItemTemplate;
import com.github.frosxt.economy.api.currency.*;
import com.github.frosxt.economy.api.menu.CurrencyMenuCatalog;
import com.github.frosxt.economy.api.message.CurrencyMessage;
import com.github.frosxt.economy.api.message.CurrencyMessageCatalog;
import com.github.frosxt.economy.menu.SlotRangeParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.math.BigDecimal;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CurrencyLoader {
    private static final String EXT = ".yml";

    private final Logger logger;

    public CurrencyLoader(final Logger logger) {
        this.logger = logger;
    }

    public List<CurrencyDefinition> loadAll(final Path currenciesDir) {
        if (!Files.isDirectory(currenciesDir)) {
            return Collections.emptyList();
        }
        final List<CurrencyDefinition> loaded = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(currenciesDir, "*.yml")) {
            for (final Path file : stream) {
                try {
                    final CurrencyDefinition definition = loadFile(file);
                    if (definition != null) {
                        loaded.add(definition);
                    }
                } catch (final Exception e) {
                    logger.log(Level.WARNING, "[Economy] Failed to load currency file: "
                            + file.getFileName(), e);
                }
            }
        } catch (final Exception e) {
            logger.log(Level.SEVERE, "[Economy] Failed to scan currencies directory", e);
        }
        return loaded;
    }

    private CurrencyDefinition loadFile(final Path file) {
        final String fileName = file.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(EXT)) {
            return null;
        }

        final String keyValue = fileName.substring(0, fileName.length() - EXT.length()).toLowerCase(Locale.ROOT);
        final CurrencyKey key = new CurrencyKey(keyValue);

        final YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());

        if (!yaml.getBoolean("enabled", true)) {
            logger.info("[Economy] Skipping disabled currency: " + keyValue);
            return null;
        }

        final CurrencyType type = parseType(yaml.getString("type", "VIRTUAL"), keyValue);
        final String displayName = yaml.getString("display-name", keyValue);
        final String singularName = yaml.getString("singular-name", keyValue);
        final String pluralName = yaml.getString("plural-name", keyValue);

        final NumberFormatPolicy numberFormat = parseNumberFormat(yaml.getConfigurationSection("number-format"));
        final int scale = numberFormat.scale();

        final ConfigurationSection rules = yaml.getConfigurationSection("rules");
        final boolean transferable = rules != null && rules.getBoolean("transferable", true);
        final boolean withdrawEnabled = rules != null && rules.getBoolean("withdraw-enabled", true);
        final boolean leaderboardEnabled = rules != null && rules.getBoolean("leaderboard-enabled", true);
        final boolean payToggleEnabled = rules != null && rules.getBoolean("paytoggle-enabled", true);
        final boolean allowDecimals = rules != null && rules.getBoolean("allow-decimals", false);
        final BigDecimal defaultBalance = readBigDecimal(rules, "default-balance", BigDecimal.ZERO);
        final BigDecimal minBalance = readBigDecimal(rules, "min-balance", BigDecimal.ZERO);
        final BigDecimal maxBalance = readBigDecimal(rules, "max-balance", BigDecimal.valueOf(-1L));

        final CommandDefinition command = parseCommand(yaml.getConfigurationSection("command"), keyValue);
        final WithdrawItemTemplate withdrawItem = parseWithdrawItem(yaml.getConfigurationSection("withdraw-item"));
        final CurrencyMenuCatalog menus = parseMenus(yaml.getConfigurationSection("menus"));
        final CurrencyMessageCatalog messages = parseMessages(yaml.getConfigurationSection("messages"));

        return new CurrencyDefinition(
                key, type, displayName, singularName, pluralName,
                scale, allowDecimals, transferable, withdrawEnabled,
                leaderboardEnabled, payToggleEnabled,
                defaultBalance, minBalance, maxBalance,
                numberFormat, command, withdrawItem, menus, messages
        );
    }

    private CurrencyType parseType(final String raw, final String keyValue) {
        if ("money".equals(keyValue)) {
            return CurrencyType.MONEY;
        }
        if ("exp".equals(keyValue)) {
            return CurrencyType.EXP;
        }
        if (raw == null || raw.isBlank()) {
            return CurrencyType.VIRTUAL;
        }
        try {
            return CurrencyType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            return CurrencyType.VIRTUAL;
        }
    }

    private NumberFormatPolicy parseNumberFormat(final ConfigurationSection section) {
        if (section == null) {
            return NumberFormatPolicy.defaultPolicy();
        }

        final int scale = section.getInt("scale", 0);
        final boolean useGrouping = section.getBoolean("use-grouping", true);
        final boolean suffixesEnabled = section.getBoolean("suffixes-enabled", false);
        final String roundModeRaw = section.getString("round-mode", "DOWN");
        RoundMode roundMode;
        try {
            roundMode = RoundMode.valueOf(roundModeRaw.toUpperCase(Locale.ROOT));
        } catch (final IllegalArgumentException e) {
            roundMode = RoundMode.DOWN;
        }

        return new NumberFormatPolicy(scale, useGrouping, suffixesEnabled, roundMode);
    }

    private CommandDefinition parseCommand(final ConfigurationSection section, final String keyValue) {
        if (section == null) {
            return new CommandDefinition(
                    keyValue, keyValue + " currency command",
                    Collections.emptyList(),
                    "prisoncore.economy." + keyValue,
                    SubcommandToggles.allEnabled()
            );
        }
        final String baseCommand = section.getString("base-command", keyValue);
        final String description = section.getString("description", keyValue + " currency command");
        final List<String> aliases = section.getStringList("aliases");
        final String permissionRoot = section.getString("permission-root", "prisoncore.economy." + keyValue);
        final ConfigurationSection subs = section.getConfigurationSection("sub-commands");
        final SubcommandToggles toggles = parseSubcommandToggles(subs);
        return new CommandDefinition(baseCommand, description, aliases, permissionRoot, toggles);
    }

    private SubcommandToggles parseSubcommandToggles(final ConfigurationSection section) {
        if (section == null) {
            return SubcommandToggles.allEnabled();
        }
        return new SubcommandToggles(
                section.getBoolean("help", true),
                section.getBoolean("give", true),
                section.getBoolean("take", true),
                section.getBoolean("set", true),
                section.getBoolean("pay", true),
                section.getBoolean("top", true),
                section.getBoolean("recalc", true),
                section.getBoolean("balance", true),
                section.getBoolean("withdraw", true),
                section.getBoolean("paytoggle", true)
        );
    }

    private WithdrawItemTemplate parseWithdrawItem(final ConfigurationSection section) {
        if (section == null) {
            return new WithdrawItemTemplate(false, "PAPER", false, "Withdraw Note", Collections.emptyList());
        }
        return new WithdrawItemTemplate(
                section.getBoolean("enabled", false),
                section.getString("material", "PAPER"),
                section.getBoolean("enchanted", false),
                section.getString("name", "Withdraw Note"),
                section.getStringList("lore")
        );
    }

    private CurrencyMenuCatalog parseMenus(final ConfigurationSection section) {
        if (section == null) {
            return new CurrencyMenuCatalog(null);
        }
        final ConfigurationSection top = section.getConfigurationSection("top-menu");
        if (top == null) {
            return new CurrencyMenuCatalog(null);
        }

        final boolean enabled = top.getBoolean("enabled", true);
        final String title = top.getString("title", "Top");
        final int rows = Math.max(1, Math.min(6, top.getInt("rows", 3)));
        final int maxSlot = rows * 9 - 1;
        final List<Integer> topSlots = top.getIntegerList("top-slots");

        final Map<String, CurrencyMenuCatalog.BorderDefinition> borders = new LinkedHashMap<>();
        final ConfigurationSection bordersSection = top.getConfigurationSection("borders");
        if (bordersSection != null) {
            for (final String borderKey : bordersSection.getKeys(false)) {
                final ConfigurationSection borderSection = bordersSection.getConfigurationSection(borderKey);
                if (borderSection == null) {
                    continue;
                }
                borders.put(borderKey, new CurrencyMenuCatalog.BorderDefinition(
                        borderSection.getString("material", "GRAY_STAINED_GLASS_PANE"),
                        borderSection.getString("name", " "),
                        borderSection.getStringList("lore"),
                        SlotRangeParser.parse(borderSection.getStringList("slots"), maxSlot, logger)
                ));
            }
        }

        final CurrencyMenuCatalog.MenuItem invalidItem = parseMenuItem(top.getConfigurationSection("invalid-item"));
        final CurrencyMenuCatalog.MenuItem validItem = parseMenuItem(top.getConfigurationSection("valid-item"));
        final CurrencyMenuCatalog.StatsItem statsItem = parseStatsItem(top.getConfigurationSection("stats-item"));

        return new CurrencyMenuCatalog(new CurrencyMenuCatalog.TopMenu(enabled, title, rows, topSlots, borders, invalidItem, validItem, statsItem));
    }

    private CurrencyMenuCatalog.MenuItem parseMenuItem(final ConfigurationSection section) {
        if (section == null) {
            return new CurrencyMenuCatalog.MenuItem("STONE", "", " ", Collections.emptyList());
        }
        return new CurrencyMenuCatalog.MenuItem(
                section.getString("material", "STONE"),
                section.getString("skull", ""),
                section.getString("name", " "),
                section.getStringList("lore")
        );
    }

    private CurrencyMenuCatalog.StatsItem parseStatsItem(final ConfigurationSection section) {
        if (section == null) {
            return new CurrencyMenuCatalog.StatsItem(
                    false, 22, "PLAYER_HEAD", "%player%", " ", Collections.emptyList());
        }
        return new CurrencyMenuCatalog.StatsItem(
                section.getBoolean("enabled", true),
                section.getInt("slot", 22),
                section.getString("material", "PLAYER_HEAD"),
                section.getString("skull", "%player%"),
                section.getString("name", " "),
                section.getStringList("lore")
        );
    }

    private CurrencyMessageCatalog parseMessages(final ConfigurationSection section) {
        if (section == null) {
            return CurrencyMessageCatalog.empty();
        }
        final Map<String, CurrencyMessage> messages = new LinkedHashMap<>();
        for (final String messageKey : section.getKeys(false)) {
            final ConfigurationSection messageSection = section.getConfigurationSection(messageKey);
            if (messageSection == null) {
                continue;
            }
            messages.put(messageKey, parseCurrencyMessage(messageKey, messageSection));
        }
        return new CurrencyMessageCatalog(messages);
    }

    private CurrencyMessage parseCurrencyMessage(final String key, final ConfigurationSection section) {
        CurrencyMessage.ChatChannel chat = CurrencyMessage.ChatChannel.disabled();
        CurrencyMessage.ActionBarChannel actionBar = CurrencyMessage.ActionBarChannel.disabled();
        CurrencyMessage.TitleChannel title = CurrencyMessage.TitleChannel.disabled();
        CurrencyMessage.SoundChannel sound = CurrencyMessage.SoundChannel.disabled();

        final ConfigurationSection chatSection = section.getConfigurationSection("chat");
        if (chatSection != null) {
            final boolean enabled = chatSection.getBoolean("enabled", true);
            final Object rawValue = chatSection.get("value");
            final List<String> lines = new ArrayList<>();
            if (rawValue instanceof List<?>) {
                for (final Object element : (List<?>) rawValue) {
                    lines.add(String.valueOf(element));
                }
            } else if (rawValue != null) {
                lines.add(rawValue.toString());
            }
            chat = new CurrencyMessage.ChatChannel(enabled, lines);
        }

        final ConfigurationSection abSection = section.getConfigurationSection("action-bar");
        if (abSection != null) {
            actionBar = new CurrencyMessage.ActionBarChannel(
                    abSection.getBoolean("enabled", false),
                    abSection.getString("value", "")
            );
        }

        final ConfigurationSection titleSection = section.getConfigurationSection("title");
        if (titleSection != null) {
            title = new CurrencyMessage.TitleChannel(
                    titleSection.getBoolean("enabled", false),
                    titleSection.getString("title", ""),
                    titleSection.getString("subtitle", ""),
                    titleSection.getInt("fade-in", 10),
                    titleSection.getInt("stay", 40),
                    titleSection.getInt("fade-out", 10)
            );
        }

        final ConfigurationSection soundSection = section.getConfigurationSection("sound");
        if (soundSection != null) {
            sound = new CurrencyMessage.SoundChannel(
                    soundSection.getBoolean("enabled", false),
                    soundSection.getString("value", ""),
                    (float) soundSection.getDouble("volume", 1.0),
                    (float) soundSection.getDouble("pitch", 1.0)
            );
        }

        return new CurrencyMessage(key, chat, actionBar, title, sound);
    }

    private BigDecimal readBigDecimal(final ConfigurationSection section, final String path, final BigDecimal fallback) {
        if (section == null || !section.contains(path)) {
            return fallback;
        }
        final Object raw = section.get(path);
        if (raw instanceof final Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (raw != null) {
            try {
                return new BigDecimal(raw.toString());
            } catch (final NumberFormatException e) {
                return fallback;
            }
        }
        return fallback;
    }
}
