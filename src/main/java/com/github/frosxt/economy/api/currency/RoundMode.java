package com.github.frosxt.economy.api.currency;

import java.math.RoundingMode;

public enum RoundMode {
    UP(RoundingMode.UP),
    DOWN(RoundingMode.DOWN),
    HALF_UP(RoundingMode.HALF_UP),
    HALF_DOWN(RoundingMode.HALF_DOWN),
    HALF_EVEN(RoundingMode.HALF_EVEN),
    CEILING(RoundingMode.CEILING),
    FLOOR(RoundingMode.FLOOR);

    private final RoundingMode bigDecimalMode;

    RoundMode(final RoundingMode bigDecimalMode) {
        this.bigDecimalMode = bigDecimalMode;
    }

    public RoundingMode toBigDecimalMode() {
        return bigDecimalMode;
    }
}
