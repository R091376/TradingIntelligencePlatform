package com.tip.patterns.breakdown;

import com.tip.indicators.AtrIndicator;
import com.tip.indicators.DonchianIndicator;
import com.tip.indicators.VolumeSmaIndicator;
import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.ConfirmationMode;
import com.tip.patterns.model.PatternDirection;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Detects a new Breakdown on the latest closed candle. Pure function — no Spring.
 *
 * <p>Docs: {@code docs/patterns/breakdown.md}
 */
public final class BreakdownDetector {

    private BreakdownDetector() {
    }

    /**
     * @param closedAscending full closed series including signal bar as last
     * @param minOpenReference min frozen reference of currently open breakdowns, or {@link Double#POSITIVE_INFINITY}
     * @param indexSegment     true if INDEX (volume fallback)
     */
    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            double minOpenReference,
            boolean indexSegment,
            BreakdownConfig config,
            Instant now
    ) {
        if (closedAscending == null || closedAscending.isEmpty()) {
            return Optional.empty();
        }

        OptionalDoublePair indicators = computeIndicators(closedAscending, config);
        if (indicators == null) {
            return Optional.empty();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        double ref = indicators.donchianLow;
        if (!(signal.close() < ref)) {
            return Optional.empty();
        }
        // PI-6 anti-spam (mirror): only lower references spawn concurrent opens
        if (!(ref < minOpenReference)) {
            return Optional.empty();
        }

        boolean volumeUnusable = indexSegment
                || indicators.avgVolume <= 0
                || signal.volume() <= 0;

        ConfirmationMode configured = config.confirmationMode();
        ConfirmationMode effective;
        boolean volumeOk;
        if (volumeUnusable && (configured == ConfirmationMode.VOLUME || configured == ConfirmationMode.BOTH)) {
            effective = ConfirmationMode.CLOSE_FALLBACK;
            volumeOk = false;
        } else {
            effective = configured;
            volumeOk = signal.volume() >= config.volumeMultiplier() * indicators.avgVolume;
        }

        double atr = indicators.atr;
        double target = ref - config.successAtrMultWithoutRetest() * atr;

        ActivePattern pattern = ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(PatternType.BREAKDOWN)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(PatternDirection.SHORT)
                .status(PatternStage.DETECTED)
                .volumeOkAtDetect(volumeOk)
                .confirmationModeUsed(effective)
                .referenceLevel(ref)
                // Column name is lookback_high; stores Donchian extreme at detect (low for breakdown)
                .lookbackHigh(ref)
                .atrAtDetect(atr)
                .volumeAtDetect(signal.volume())
                .entryPrice(signal.close())
                .stopLevel(ref)
                .targetLevel(target)
                .detectCandleTime(signal.time())
                .detectedAt(now)
                .mfePrice(signal.high())
                .maePrice(signal.low())
                .durationCandles(1)
                .detectorVersion(config.detectorVersion())
                .build();

        return Optional.of(pattern);
    }

    private static OptionalDoublePair computeIndicators(List<Candle> closed, BreakdownConfig config) {
        var atr = AtrIndicator.latest(closed, config.atrPeriod());
        var don = DonchianIndicator.priorLowestLow(closed, config.lookbackCandles());
        var vol = VolumeSmaIndicator.priorAverage(closed, config.volumeAvgPeriod());
        if (atr.isEmpty() || don.isEmpty() || vol.isEmpty()) {
            if (atr.isEmpty() || don.isEmpty()) {
                return null;
            }
            return new OptionalDoublePair(atr.getAsDouble(), don.getAsDouble(), 0.0);
        }
        return new OptionalDoublePair(atr.getAsDouble(), don.getAsDouble(), vol.getAsDouble());
    }

    private record OptionalDoublePair(double atr, double donchianLow, double avgVolume) {
    }
}
