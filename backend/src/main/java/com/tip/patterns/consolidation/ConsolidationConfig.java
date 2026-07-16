package com.tip.patterns.consolidation;

public record ConsolidationConfig(
        int atrPeriod,
        int windowCandles,
        double rangeAtrMult,
        int maxDurationCandles,
        double tightenRatio,
        String detectorVersion
) {
    public static ConsolidationConfig defaults() {
        return new ConsolidationConfig(14, 10, 1.5, 30, 0.85, "consolidation-v1");
    }
}
