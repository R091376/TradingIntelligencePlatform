package com.tip.patterns.model;

public enum PatternType {
    BREAKOUT,
    BREAKDOWN,
    CONSOLIDATION,
    VOLUME_BREAKOUT,
    /** Bullish pin bar (Nison hammer geometry). */
    HAMMER,
    /** Bearish pin bar (Nison shooting star geometry). */
    SHOOTING_STAR,
    /** Nison bullish engulfing. */
    BULLISH_ENGULFING,
    /** Nison bearish engulfing. */
    BEARISH_ENGULFING,
    /** Mother–inside–breakout (direction set at break). */
    INSIDE_BAR,
    /** Confirmed swing higher high. */
    HIGHER_HIGH,
    /** Confirmed swing lower low. */
    LOWER_LOW;

    public String wireValue() {
        return name().toLowerCase();
    }

    public boolean isPinBar() {
        return this == HAMMER || this == SHOOTING_STAR;
    }

    public boolean isEngulfing() {
        return this == BULLISH_ENGULFING || this == BEARISH_ENGULFING;
    }

    public boolean isStructure() {
        return this == HIGHER_HIGH || this == LOWER_LOW;
    }
}
