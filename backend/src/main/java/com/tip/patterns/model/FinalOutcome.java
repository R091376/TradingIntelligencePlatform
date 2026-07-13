package com.tip.patterns.model;

public enum FinalOutcome {
    SUCCEEDED,
    FAILED,
    EXPIRED;

    public String wireValue() {
        return name().toLowerCase();
    }
}
