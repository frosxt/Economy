package com.github.frosxt.economy.api.currency;

/**
 * Backing semantics of a currency. Determines which provider handles its transactions.
 */
public enum CurrencyType {
    /** Persisted virtual balance bridged out to Vault as the server's primary money. */
    MONEY,
    /** Backed by live {@code Player.getTotalExperience()}; no persistence. */
    EXP,
    /** Custom persisted virtual balance with no special semantics. */
    VIRTUAL
}
