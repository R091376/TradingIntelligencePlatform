package com.tip.patterns.pinbar;

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
 * Detects Hammer / Shooting Star on the latest closed candle. Pure — no Spring.
 * Docs: {@code docs/patterns/hammer-shooting-star.md}
 */
public final class PinBarDetector {

    private PinBarDetector() {
    }

    /**
     * @param pinBarAlreadyOpen anti-spam: skip if any open hammer or shooting_star on series
     */
    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            boolean pinBarAlreadyOpen,
            PinBarConfig config,
            Instant now
    ) {
        if (pinBarAlreadyOpen || closedAscending == null || closedAscending.isEmpty() || config == null) {
            return Optional.empty();
        }

        var atrOpt = AtrIndicator.latest(closedAscending, config.atrPeriod());
        if (atrOpt.isEmpty()) {
            return Optional.empty();
        }
        double atr = atrOpt.getAsDouble();
        if (!(atr > 0)) {
            return Optional.empty();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        Optional<PinBarGeometry.Kind> kind = PinBarGeometry.classify(signal, atr, config);
        if (kind.isEmpty()) {
            return Optional.empty();
        }

        if (config.requireTrendContext() && !trendContextOk(closedAscending, kind.get(), config)) {
            return Optional.empty();
        }

        return Optional.of(buildInstance(symbolId, timeframe, signal, atr, kind.get(), config, now));
    }

    static boolean trendContextOk(
            List<Candle> closedAscending,
            PinBarGeometry.Kind kind,
            PinBarConfig config
    ) {
        int n = closedAscending.size();
        // Need signal + lookback prior bars for SMA of closes before signal
        int lookback = Math.max(2, config.trendLookback());
        if (n < lookback + 1) {
            // Fall back to simple 3-bar bias when history is short but ATR exists
            if (n < 4) {
                return true;
            }
            Candle prev = closedAscending.get(n - 2);
            Candle older = closedAscending.get(n - 4);
            return kind == PinBarGeometry.Kind.HAMMER
                    ? prev.close() < older.close()
                    : prev.close() > older.close();
        }

        double sum = 0;
        for (int i = n - 1 - lookback; i < n - 1; i++) {
            sum += closedAscending.get(i).close();
        }
        double sma = sum / lookback;
        double prevClose = closedAscending.get(n - 2).close();
        if (kind == PinBarGeometry.Kind.HAMMER) {
            return prevClose < sma;
        }
        return prevClose > sma;
    }

    private static ActivePattern buildInstance(
            String symbolId,
            String timeframe,
            Candle signal,
            double atr,
            PinBarGeometry.Kind kind,
            PinBarConfig config,
            Instant now
    ) {
        boolean hammer = kind == PinBarGeometry.Kind.HAMMER;
        double entry = signal.close();
        double stop = hammer ? signal.low() : signal.high();
        double target = hammer
                ? entry + config.successAtrMult() * atr
                : entry - config.successAtrMult() * atr;
        // Schema reuses lookback_high: pair extreme of the pin bar
        double lookbackPair = hammer ? signal.high() : signal.low();

        return ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(hammer ? PatternType.HAMMER : PatternType.SHOOTING_STAR)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(hammer ? PatternDirection.LONG : PatternDirection.SHORT)
                .status(PatternStage.DETECTED)
                .volumeOkAtDetect(false)
                .confirmationModeUsed(ConfirmationMode.CLOSE)
                .referenceLevel(stop)
                .lookbackHigh(lookbackPair)
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
