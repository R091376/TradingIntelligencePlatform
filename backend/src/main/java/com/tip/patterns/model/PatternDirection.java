package com.tip.patterns.model;

public enum PatternDirection {
    LONG,
    SHORT;

    public String wireValue() {
        return name().toLowerCase();
    }
}
