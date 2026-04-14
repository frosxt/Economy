package com.github.frosxt.economy.menu;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public final class SlotRangeParser {
    private SlotRangeParser() {
        throw new UnsupportedOperationException("Utiility classes cannot be instantiated");
    }

    public static Set<Integer> parse(final List<String> raw, final int maxSlot, final Logger logger) {
        final Set<Integer> out = new LinkedHashSet<>();
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        for (final String entry : raw) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            for (final String token : entry.split(",")) {
                parseToken(token.trim(), maxSlot, out, logger);
            }
        }
        return out;
    }

    private static void parseToken(final String token, final int maxSlot, final Set<Integer> out, final Logger logger) {
        if (token.isEmpty()) {
            return;
        }
        final int dash = token.indexOf('-');
        if (dash < 0) {
            addSingle(token, maxSlot, out, logger);
            return;
        }

        final String fromStr = token.substring(0, dash).trim();
        final String toStr = token.substring(dash + 1).trim();
        try {
            final int from = Integer.parseInt(fromStr);
            final int to = Integer.parseInt(toStr);
            final int lo = Math.min(from, to);
            final int hi = Math.max(from, to);
            for (int i = lo; i <= hi; i++) {
                if (i < 0 || i > maxSlot) {
                    logger.warning("[Economy] menu slot out of bounds: " + i);
                    continue;
                }
                out.add(i);
            }
        } catch (final NumberFormatException ex) {
            logger.warning("[Economy] invalid menu slot range: " + token);
        }
    }

    private static void addSingle(final String token, final int maxSlot, final Set<Integer> out, final Logger logger) {
        try {
            final int value = Integer.parseInt(token);
            if (value < 0 || value > maxSlot) {
                logger.warning("[Economy] menu slot out of bounds: " + value);
                return;
            }
            out.add(value);
        } catch (final NumberFormatException ex) {
            logger.warning("[Economy] invalid menu slot token: " + token);
        }
    }
}
