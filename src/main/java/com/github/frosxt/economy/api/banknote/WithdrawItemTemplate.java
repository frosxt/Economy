package com.github.frosxt.economy.api.banknote;

import java.util.List;

/**
 * Per-currency template for the physical withdraw note item. Placeholders like
 * {@code %amount%} and {@code %player%} are substituted at issue time.
 */
public record WithdrawItemTemplate(
        boolean enabled,
        String material,
        boolean enchanted,
        String name,
        List<String> lore
) {

    public WithdrawItemTemplate {
        lore = List.copyOf(lore);
    }
}
