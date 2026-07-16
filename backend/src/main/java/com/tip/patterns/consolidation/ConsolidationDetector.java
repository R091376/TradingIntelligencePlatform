package com.tip.patterns.consolidation;

import com.tip.indicators.AtrIndicator;
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
 * Detects ATR-normalized multi-bar range compression.
 * Docs: {@code docs/patterns/consolidation.md}
 */
public final class ConsolidationDetector {

    private ConsolidationDetector() {
    }

    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            boolean alreadyOpen,
            ConsolidationConfig config,
            Instant now
    ) {
        if (alreadyOpen || closedAscending == null || config == null) {
            return Optional.empty();
        }
        int m = config.windowCandles();
        if (closedAscending.size() < m) {
            return Optional.empty();
        }
        var atrOpt = AtrIndicator.latest(closedAscending, config.atrPeriod());
        if (atrOpt.isEmpty() || !(atrOpt.getAsDouble() > 0)) {
            return Optional.empty();
        }
        double atr = atrOpt.getAsDouble();

        int n = closedAscending.size();
        double rangeHigh = Double.NEGATIVE_INFINITY;
        double rangeLow = Double.POSITIVE_INFINITY;
        for (int i = n - m; i < n; i++) {
            Candle c = closedAscending.get(i);
            rangeHigh = Math.max(rangeHigh, c.high());
            rangeLow = Math.min(rangeLow, c.low());
        }
        double range = rangeHigh - rangeLow;
        if (!(range <= config.rangeAtrMult() * atr) || !(range > 0)) {
            return Optional.empty();
        }

        // Only detect on the first bar the window becomes tight (avoid re-firing every bar):
        // if the prior window ending at n-2 was also tight, skip.
        if (n > m) {
            double prevHigh = Double.NEGATIVE_INFINITY;
            double prevLow = Double.POSITIVE_INFINITY;
            for (int i = n - 1 - m; i < n - 1; i++) {
                Candle c = closedAscending.get(i);
                prevHigh = Math.max(prevHigh, c.high());
                prevLow = Math.min(prevLow, c.low());
            }
            double prevRange = prevHigh - prevLow;
            if (prevRange <= config.rangeAtrMult() * atr) {
                return Optional.empty();
            }
        }

        Candle signal = closedAscending.get(n - 1);
        double mid = (rangeHigh + rangeLow) / 2.0;

        return Optional.of(ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(PatternType.CONSOLIDATION)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(PatternDirection.LONG) // structure; expansion can be either side
                .status(PatternStage.DETECTED)
                .confirmationModeUsed(ConfirmationMode.CLOSE)
                .referenceLevel(rangeLow)
                .lookbackHigh(rangeHigh)
                .atrAtDetect(atr)
                .volumeAtDetect(signal.volume())
                .entryPrice(mid)
                .stopLevel(rangeLow)
                .targetLevel(rangeHigh) // placeholder; success is break either side
                .detectCandleTime(signal.time())
                .detectedAt(now)
                .mfePrice(signal.high())
                .maePrice(signal.low())
                .durationCandles(1)
                .detectorVersion(config.detectorVersion())
                .build());
    }
}
