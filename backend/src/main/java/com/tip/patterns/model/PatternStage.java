package com.tip.patterns.model;

/**
 * Lifecycle stages including terminals. Display status uses highest non-terminal ordinal among flags.
 */
public enum PatternStage {
    DETECTED,
    CONFIRMED,
    RETESTED,
    STRENGTHENED,
    SUCCEEDED,
    FAILED,
    EXPIRED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == EXPIRED;
    }

    public String wireValue() {
        return name().toLowerCase();
    }
}
