package com.github.frosxt.economy.leaderboard.render;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.leaderboard.LeaderboardEntry;
import com.github.frosxt.economy.api.leaderboard.LeaderboardSnapshot;
import com.github.frosxt.economy.api.menu.CurrencyMenuCatalog;
import com.github.frosxt.prisoncore.commons.bukkit.color.ColorTranslator;
import com.github.frosxt.prisoncore.commons.bukkit.item.BukkitItemBuilder;
import com.github.frosxt.prisoncore.menu.api.MenuDescriptor;
import com.github.frosxt.prisoncore.menu.api.MenuService;
import com.github.frosxt.prisoncore.menu.api.click.ClickHandler;
import com.github.frosxt.prisoncore.menu.api.layout.SlotDescriptor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class LeaderboardMenuRenderer {
    private final MenuService menuService;

    public LeaderboardMenuRenderer(final MenuService menuService) {
        this.menuService = menuService;
    }

    public void open(final Player viewer, final CurrencyDefinition definition, final LeaderboardSnapshot snapshot) {
        final CurrencyMenuCatalog.TopMenu top = definition.menus().topMenu();
        if (top == null || !top.enabled()) {
            viewer.sendMessage("This currency does not have a top menu configured.");
            return;
        }

        final int rows = Math.max(1, Math.min(6, top.rows()));
        final MenuDescriptor.Builder builder = MenuDescriptor.builder(
                "economy:" + definition.key().value() + ":top",
                ColorTranslator.colorize(top.title()),
                rows
        );

        final Set<Integer> contentSlots = new HashSet<>(top.topSlots());
        final CurrencyMenuCatalog.StatsItem stats = top.statsItem();
        if (stats != null && stats.enabled()) {
            contentSlots.add(stats.slot());
        }

        for (final CurrencyMenuCatalog.BorderDefinition border : top.borders().values()) {
            final SlotDescriptor borderSlot = renderBorder(border);
            for (final int slot : border.slots()) {
                if (contentSlots.contains(slot)) {
                    continue;
                }
                builder.slot(slot, borderSlot);
            }
        }

        final List<Integer> topSlots = top.topSlots();
        final List<LeaderboardEntry> entries = snapshot.entries();
        for (int i = 0; i < topSlots.size(); i++) {
            final int slot = topSlots.get(i);
            if (i < entries.size()) {
                builder.slot(slot, renderValidEntry(definition, top.validItem(), entries.get(i)));
            } else {
                builder.slot(slot, renderInvalidEntry(top.invalidItem()));
            }
        }

        if (stats != null && stats.enabled()) {
            final Optional<LeaderboardEntry> viewerEntry = snapshot.findByPlayer(viewer.getUniqueId());
            builder.slot(stats.slot(), renderStatsItem(definition, stats, viewer, viewerEntry));
        }

        menuService.open(viewer.getUniqueId(), builder.build());
    }

    private SlotDescriptor renderBorder(final CurrencyMenuCatalog.BorderDefinition border) {
        final Material material = parseMaterial(border.material(), Material.GRAY_STAINED_GLASS_PANE);
        return new SlotDescriptor(
                () -> BukkitItemBuilder.of(material)
                        .name(border.name())
                        .lore(border.lore())
                        .build(),
                noOp()
        );
    }

    private SlotDescriptor renderValidEntry(
            final CurrencyDefinition definition,
            final CurrencyMenuCatalog.MenuItem template,
            final LeaderboardEntry entry) {
        final Material material = parseMaterial(template.material(), Material.PLAYER_HEAD);
        return new SlotDescriptor(
                () -> {
                    final BukkitItemBuilder itemBuilder = BukkitItemBuilder.of(material)
                            .name(replacePlaceholders(template.name(), definition, entry))
                            .lore(template.lore().stream()
                                    .map(line -> replacePlaceholders(line, definition, entry))
                                    .toList());
                    if (material == Material.PLAYER_HEAD) {
                        itemBuilder.skullOwner(entry.username());
                    }
                    return itemBuilder.build();
                },
                noOp()
        );
    }

    private SlotDescriptor renderInvalidEntry(final CurrencyMenuCatalog.MenuItem template) {
        final Material material = parseMaterial(template.material(), Material.PLAYER_HEAD);
        return new SlotDescriptor(
                () -> {
                    final BukkitItemBuilder itemBuilder = BukkitItemBuilder.of(material)
                            .name(template.name())
                            .lore(template.lore());
                    if (material == Material.PLAYER_HEAD && template.skull() != null && !template.skull().isEmpty()) {
                        itemBuilder.skullOwner(template.skull());
                    }
                    return itemBuilder.build();
                },
                noOp()
        );
    }

    private SlotDescriptor renderStatsItem(
            final CurrencyDefinition definition,
            final CurrencyMenuCatalog.StatsItem stats,
            final Player viewer,
            final Optional<LeaderboardEntry> viewerEntry) {
        final Material material = parseMaterial(stats.material(), Material.PLAYER_HEAD);
        final String rank = viewerEntry.map(e -> String.valueOf(e.rank())).orElse("?");
        final String amount = viewerEntry.map(e -> e.amount().toPlainString()).orElse("0");

        return new SlotDescriptor(
                () -> {
                    final BukkitItemBuilder itemBuilder = BukkitItemBuilder.of(material)
                            .name(stats.name()
                                    .replace("%player%", viewer.getName())
                                    .replace("%rank%", rank)
                                    .replace("%amount%", amount)
                                    .replace("%currency%", definition.displayName()))
                            .lore(stats.lore().stream()
                                    .map(line -> line
                                            .replace("%player%", viewer.getName())
                                            .replace("%rank%", rank)
                                            .replace("%amount%", amount)
                                            .replace("%currency%", definition.displayName()))
                                    .toList());
                    if (material == Material.PLAYER_HEAD) {
                        itemBuilder.skullOwner(viewer.getName());
                    }
                    return itemBuilder.build();
                },
                noOp()
        );
    }

    private String replacePlaceholders(
            final String raw,
            final CurrencyDefinition definition,
            final LeaderboardEntry entry) {
        return raw
                .replace("%player%", entry.username())
                .replace("%rank%", String.valueOf(entry.rank()))
                .replace("%amount%", entry.amount().toPlainString())
                .replace("%currency%", definition.displayName());
    }

    private Material parseMaterial(final String raw, final Material fallback) {
        try {
            return Material.valueOf(raw.toUpperCase());
        } catch (final IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }

    private ClickHandler noOp() {
        return context -> {
        };
    }
}
