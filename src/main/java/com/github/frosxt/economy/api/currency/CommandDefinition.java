package com.github.frosxt.economy.api.currency;

import java.util.List;

/**
 * Command-layer configuration for a currency: the base command name, aliases,
 * description, permission root, and which subcommands are enabled.
 */
public record CommandDefinition(
        String baseCommand,
        String description,
        List<String> aliases,
        String permissionRoot,
        SubcommandToggles subcommands
) {

    public CommandDefinition {
        aliases = List.copyOf(aliases);
    }
}
