package com.github.frosxt.economy.api.menu;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-currency menu configuration parsed from the {@code menus:} block of a currency YAML.
 * Currently only the leaderboard ({@code top-menu}) is represented.
 */
public record CurrencyMenuCatalog(
        TopMenu topMenu
) {

    public record TopMenu(
            boolean enabled,
            String title,
            int rows,
            List<Integer> topSlots,
            Map<String, BorderDefinition> borders,
            MenuItem invalidItem,
            MenuItem validItem,
            StatsItem statsItem
    ) {

        public TopMenu {
            topSlots = List.copyOf(topSlots);
            borders = Map.copyOf(borders);
        }
    }

    public record BorderDefinition(
            String material,
            String name,
            List<String> lore,
            Set<Integer> slots
    ) {

        public BorderDefinition {
            lore = List.copyOf(lore);
            slots = Set.copyOf(slots);
        }
    }

    public record MenuItem(
            String material,
            String skull,
            String name,
            List<String> lore
    ) {

        public MenuItem {
            lore = List.copyOf(lore);
        }
    }

    public record StatsItem(
            boolean enabled,
            int slot,
            String material,
            String skull,
            String name,
            List<String> lore
    ) {

        public StatsItem {
            lore = List.copyOf(lore);
        }
    }
}
