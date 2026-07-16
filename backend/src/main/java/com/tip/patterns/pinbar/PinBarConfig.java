package com.tip.patterns.pinbar;

/**
 * Tunables for Hammer / Shooting Star (shared geometry + short lifecycle).
 * Docs: {@code docs/patterns/hammer-shooting-star.md}
 */
public record PinBarConfig(
        int atrPeriod,
        double shadowBodyMult,
        double maxBodyRangeRatio,
        double maxOppositeWickRangeRatio,
        double minRangeAtrMult,
        double successAtrMult,
        boolean requireTrendContext,
        int trendLookback,
        int maxCandlesAfterDetect,
        String detectorVersion
) {
    public static PinBarConfig defaults() {
        return new PinBarConfig(
                14,
                2.0,
                0.35,
                0.25,
                0.5,
                1.5,
                true,
                10,
                20,
                "pinbar-v1"
        );
    }
}
