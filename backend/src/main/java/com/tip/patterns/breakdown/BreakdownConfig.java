package com.tip.patterns.breakdown;

import com.tip.indicators.AtrIndicator;
import com.tip.indicators.DonchianIndicator;
import com.tip.indicators.VolumeSmaIndicator;
import com.tip.patterns.model.ConfirmationMode;

/**
 * Tunables for Breakdown detection and lifecycle (pure config, no Spring).
 * Same knobs as Breakout; independent detector version.
 */
public record BreakdownConfig(
        int lookbackCandles,
        int atrPeriod,
        int volumeAvgPeriod,
        ConfirmationMode confirmationMode,
        double volumeMultiplier,
        double retestAtrMult,
        double strengthenAtrMult,
        double successRr,
        double successAtrMultWithoutRetest,
        String detectorVersion
) {
    public static BreakdownConfig defaults() {
        return new BreakdownConfig(
                DonchianIndicator.DEFAULT_PERIOD,
                AtrIndicator.DEFAULT_PERIOD,
                VolumeSmaIndicator.DEFAULT_PERIOD,
                ConfirmationMode.BOTH,
                1.5,
                0.25,
                1.0,
                2.0,
                2.0,
                "breakdown-v1"
        );
    }

    public BreakdownConfig {
        if (lookbackCandles < 1 || atrPeriod < 1 || volumeAvgPeriod < 1) {
            throw new IllegalArgumentException("periods must be >= 1");
        }
        if (volumeMultiplier <= 0 || retestAtrMult < 0 || strengthenAtrMult < 0
                || successRr <= 0 || successAtrMultWithoutRetest <= 0) {
            throw new IllegalArgumentException("invalid mult/rr values");
        }
        if (confirmationMode == null) {
            confirmationMode = ConfirmationMode.BOTH;
        }
        if (detectorVersion == null || detectorVersion.isBlank()) {
            detectorVersion = "breakdown-v1";
        }
    }
}
