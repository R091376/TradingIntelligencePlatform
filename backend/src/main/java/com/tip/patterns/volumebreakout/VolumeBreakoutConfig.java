package com.tip.patterns.volumebreakout;

public record VolumeBreakoutConfig(
        int atrPeriod,
        int volumeAvgPeriod,
        double volumeMultiplier,
        double minRangeAtrMult,
        double successAtrMult,
        int maxCandlesAfterDetect,
        String detectorVersion
) {
    public static VolumeBreakoutConfig defaults() {
        return new VolumeBreakoutConfig(14, 20, 2.0, 0.5, 1.5, 20, "volume-breakout-v1");
    }
}
