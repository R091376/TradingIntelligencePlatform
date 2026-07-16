package com.tip.patterns.engulfing;

public record EngulfingConfig(
        int atrPeriod,
        double minRangeAtrMult,
        double successAtrMult,
        int maxCandlesAfterDetect,
        String detectorVersion
) {
    public static EngulfingConfig defaults() {
        return new EngulfingConfig(14, 0.5, 1.5, 20, "engulfing-v1");
    }
}
