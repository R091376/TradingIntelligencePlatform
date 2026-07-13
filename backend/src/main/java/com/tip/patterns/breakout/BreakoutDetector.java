package com.tip.patterns.breakout;

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
 * Detects a new Breakout on the latest closed candle. Pure function — no Spring.
 *
 * <p>Docs: {@code docs/patterns/breakout.md}
 */
public final class BreakoutDetector {

    private BreakoutDetector() {
    }

    /**
     * @param closedAscending full closed series including signal bar as last
     * @param maxOpenReference max frozen reference of currently open breakouts, or {@link Double#NEGATIVE_INFINITY}
     * @param indexSegment     true if INDEX (volume fallback)
     */
    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            double maxOpenReference,
            boolean indexSegment,
            BreakoutConfig config,
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
        double ref = indicators.donchianHigh;
        if (!(signal.close() > ref)) {
            return Optional.empty();
        }
        // PI-6 anti-spam
        if (!(ref > maxOpenReference)) {
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
        double target = ref + config.successAtrMultWithoutRetest() * atr;

        ActivePattern pattern = ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(PatternType.BREAKOUT)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(PatternDirection.LONG)
                .status(PatternStage.DETECTED)
                .volumeOkAtDetect(volumeOk)
                .confirmationModeUsed(effective)
                .referenceLevel(ref)
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

    private static OptionalDoublePair computeIndicators(List<Candle> closed, BreakoutConfig config) {
        var atr = AtrIndicator.latest(closed, config.atrPeriod());
        var don = DonchianIndicator.priorHighestHigh(closed, config.lookbackCandles());
        var vol = VolumeSmaIndicator.priorAverage(closed, config.volumeAvgPeriod());
        if (atr.isEmpty() || don.isEmpty() || vol.isEmpty()) {
            // Volume SMA may fail if period long; if volume path not needed we could relax —
            // design requires prior avg for volume check; still need bars for Donchian/ATR.
            // If only vol empty but we have atr+don and enough bars, use 0 avg → volumeUnusable path.
            if (atr.isEmpty() || don.isEmpty()) {
                return null;
            }
            return new OptionalDoublePair(atr.getAsDouble(), don.getAsDouble(), 0.0);
        }
        return new OptionalDoublePair(atr.getAsDouble(), don.getAsDouble(), vol.getAsDouble());
    }

    private record OptionalDoublePair(double atr, double donchianHigh, double avgVolume) {
    }
}
