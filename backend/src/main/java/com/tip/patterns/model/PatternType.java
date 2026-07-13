package com.tip.patterns.model;

public enum PatternType {
    BREAKOUT,
    BREAKDOWN,
    CONSOLIDATION,
    VOLUME_BREAKOUT;

    public String wireValue() {
        return name().toLowerCase();
    }
}
