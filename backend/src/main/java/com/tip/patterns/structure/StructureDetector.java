package com.tip.patterns.structure;

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

/** Higher High / Lower Low when a new fractal pivot confirms. */
public final class StructureDetector {

    private StructureDetector() {
    }

    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            boolean alreadyOpen,
            StructureConfig config,
            Instant now
    ) {
        if (alreadyOpen || closedAscending == null || config == null) {
            return Optional.empty();
        }
        int width = config.fractalWidth();
        int n = closedAscending.size();
        var pivotIdxOpt = SwingPoints.confirmedPivotIndex(n, width);
        if (pivotIdxOpt.isEmpty()) {
            return Optional.empty();
        }
        int i = pivotIdxOpt.getAsInt();
        var atrOpt = AtrIndicator.latest(closedAscending, config.atrPeriod());
        if (atrOpt.isEmpty() || !(atrOpt.getAsDouble() > 0)) {
            return Optional.empty();
        }
        double atr = atrOpt.getAsDouble();
        Candle signal = closedAscending.get(n - 1);
        Candle pivot = closedAscending.get(i);

        boolean hh = SwingPoints.isPivotHigh(closedAscending, i, width);
        boolean ll = SwingPoints.isPivotLow(closedAscending, i, width);
        if (hh == ll) {
            // rare equal high/low fractal — skip
            return Optional.empty();
        }

        if (hh) {
            List<SwingPoints.Pivot> priors = SwingPoints.pivotHighsBefore(closedAscending, i, width);
            if (priors.isEmpty()) {
                return Optional.empty();
            }
            SwingPoints.Pivot prev = priors.get(priors.size() - 1);
            if (!(pivot.high() > prev.price())) {
                return Optional.empty();
            }
            List<SwingPoints.Pivot> lows = SwingPoints.pivotLowsBefore(closedAscending, i, width);
            double rawStop = lows.isEmpty()
                    ? pivot.high() - atr
                    : lows.get(lows.size() - 1).price();
            double stop = sanitizeStop(true, signal.close(), rawStop, atr);
            return Optional.of(build(
                    symbolId, timeframe, PatternType.HIGHER_HIGH, PatternDirection.LONG,
                    signal, pivot.high(), stop, atr, config, now));
        }

        List<SwingPoints.Pivot> priors = SwingPoints.pivotLowsBefore(closedAscending, i, width);
        if (priors.isEmpty()) {
            return Optional.empty();
        }
        SwingPoints.Pivot prev = priors.get(priors.size() - 1);
        if (!(pivot.low() < prev.price())) {
            return Optional.empty();
        }
        List<SwingPoints.Pivot> highs = SwingPoints.pivotHighsBefore(closedAscending, i, width);
        double rawStop = highs.isEmpty()
                ? pivot.low() + atr
                : highs.get(highs.size() - 1).price();
        double stop = sanitizeStop(false, signal.close(), rawStop, atr);
        return Optional.of(build(
                symbolId, timeframe, PatternType.LOWER_LOW, PatternDirection.SHORT,
                signal, pivot.low(), stop, atr, config, now));
    }

    /**
     * Ensure long stop is strictly below entry and short stop strictly above entry,
     * so invalidation is tradable (avoids instant FAIL from inverted swing stops).
     */
    static double sanitizeStop(boolean longSide, double entry, double rawStop, double atr) {
        double buffer = atr > 0 ? atr : Math.max(Math.abs(entry) * 1e-4, 1e-6);
        if (longSide) {
            double stop = rawStop;
            if (!(stop < entry)) {
                stop = entry - buffer;
            }
            if (!(stop < entry)) {
                stop = entry - Math.max(Math.abs(entry) * 1e-4, 1e-6);
            }
            return stop;
        }
        double stop = rawStop;
        if (!(stop > entry)) {
            stop = entry + buffer;
        }
        if (!(stop > entry)) {
            stop = entry + Math.max(Math.abs(entry) * 1e-4, 1e-6);
        }
        return stop;
    }

    private static ActivePattern build(
            String symbolId,
            String timeframe,
            PatternType type,
            PatternDirection direction,
            Candle signal,
            double structurePrice,
            double stop,
            double atr,
            StructureConfig config,
            Instant now
    ) {
        boolean longSide = direction == PatternDirection.LONG;
        double entry = signal.close();
        double target = longSide
                ? entry + config.successAtrMult() * atr
                : entry - config.successAtrMult() * atr;
        return ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(type)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(direction)
                .status(PatternStage.DETECTED)
                .confirmationModeUsed(ConfirmationMode.CLOSE)
                .referenceLevel(stop)
                .lookbackHigh(structurePrice)
                .atrAtDetect(atr)
                .volumeAtDetect(signal.volume())
                .entryPrice(entry)
                .stopLevel(stop)
                .targetLevel(target)
                .detectCandleTime(signal.time())
                .detectedAt(now)
                .mfePrice(signal.high())
                .maePrice(signal.low())
                .durationCandles(1)
                .detectorVersion(config.detectorVersion())
                .build();
    }
}

