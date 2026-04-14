package com.github.frosxt.economy.api.currency;

/**
 * Per-currency flags controlling which subcommands the registrar exposes.
 * A {@code false} flag means the subcommand is not registered at all — attempting
 * to invoke it falls through to Bukkit's unknown command handler.
 */
public record SubcommandToggles(
        boolean help,
        boolean give,
        boolean take,
        boolean set,
        boolean pay,
        boolean top,
        boolean recalc,
        boolean balance,
        boolean withdraw,
        boolean payToggle
) {

    public static SubcommandToggles allEnabled() {
        return new SubcommandToggles(true, true, true, true, true, true, true, true, true, true);
    }
}
