package com.github.frosxt.economy.currency.loader;

import com.github.frosxt.economy.api.currency.CurrencyDefinition;
import com.github.frosxt.economy.api.currency.CurrencyType;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class CurrencyValidator {
    private final Logger logger;

    public CurrencyValidator(final Logger logger) {
        this.logger = logger;
    }

    public List<CurrencyDefinition> validate(final List<CurrencyDefinition> candidates) {
        final Set<String> seenBaseCommands = new HashSet<>();
        final Set<String> seenAliases = new HashSet<>();
        final List<CurrencyDefinition> accepted = new java.util.ArrayList<>();

        for (final CurrencyDefinition definition : candidates) {
            if (!isStructurallyValid(definition)) {
                continue;
            }

            final String base = definition.command().baseCommand().toLowerCase();
            if (seenBaseCommands.contains(base)) {
                logger.log(Level.WARNING, "[Economy] Duplicate base command '" + base + "' — rejecting currency " + definition.key().value());
                continue;
            }

            boolean aliasConflict = false;
            for (final String alias : definition.command().aliases()) {
                final String normalized = alias.toLowerCase();
                if (seenBaseCommands.contains(normalized) || seenAliases.contains(normalized)) {
                    logger.log(Level.WARNING, "[Economy] Duplicate alias '" + alias + "' — rejecting currency " + definition.key().value());
                    aliasConflict = true;
                    break;
                }
            }
            if (aliasConflict) {
                continue;
            }

            seenBaseCommands.add(base);
            for (final String alias : definition.command().aliases()) {
                seenAliases.add(alias.toLowerCase());
            }
            accepted.add(definition);
        }
        return accepted;
    }

    private boolean isStructurallyValid(final CurrencyDefinition definition) {
        final String keyValue = definition.key().value();

        if ("money".equals(keyValue) && definition.type() != CurrencyType.MONEY) {
            logger.log(Level.WARNING, "[Economy] money.yml must declare type: MONEY — rejected");
            return false;
        }
        if ("exp".equals(keyValue) && definition.type() != CurrencyType.EXP) {
            logger.log(Level.WARNING, "[Economy] exp.yml must declare type: EXP — rejected");
            return false;
        }
        if (definition.type() == CurrencyType.EXP && definition.allowDecimals()) {
            logger.log(Level.WARNING, "[Economy] EXP currencies cannot allow decimals — rejected " + keyValue);
            return false;
        }
        if (definition.command() == null || definition.command().baseCommand() == null || definition.command().baseCommand().isBlank()) {
            logger.log(Level.WARNING, "[Economy] Currency " + keyValue + " missing base command");
            return false;
        }
        return true;
    }
}
