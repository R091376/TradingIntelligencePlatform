package com.tip.patterns.engulfing;

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

/** Nison engulfing on the latest two closed bars. */
public final class EngulfingDetector {

    private EngulfingDetector() {
    }

    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            boolean alreadyOpen,
            EngulfingConfig config,
            Instant now
    ) {
        if (alreadyOpen || closedAscending == null || closedAscending.size() < 2 || config == null) {
            return Optional.empty();
        }
        var atrOpt = AtrIndicator.latest(closedAscending, config.atrPeriod());
        if (atrOpt.isEmpty() || !(atrOpt.getAsDouble() > 0)) {
            return Optional.empty();
        }
        double atr = atrOpt.getAsDouble();
        Candle prior = closedAscending.get(closedAscending.size() - 2);
        Candle signal = closedAscending.get(closedAscending.size() - 1);
        double range = signal.high() - signal.low();
        if (!(range >= config.minRangeAtrMult() * atr)) {
            return Optional.empty();
        }

        boolean bullish = isBullishEngulfing(prior, signal);
        boolean bearish = isBearishEngulfing(prior, signal);
        if (bullish == bearish) {
            return Optional.empty();
        }

        boolean longSide = bullish;
        double entry = signal.close();
        double stop = longSide
                ? Math.min(signal.low(), prior.low())
                : Math.max(signal.high(), prior.high());
        double target = longSide
                ? entry + config.successAtrMult() * atr
                : entry - config.successAtrMult() * atr;
        double confirmExtreme = longSide ? signal.high() : signal.low();

        return Optional.of(ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(longSide ? PatternType.BULLISH_ENGULFING : PatternType.BEARISH_ENGULFING)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(longSide ? PatternDirection.LONG : PatternDirection.SHORT)
                .status(PatternStage.DETECTED)
                .confirmationModeUsed(ConfirmationMode.CLOSE)
                .referenceLevel(stop)
                .lookbackHigh(confirmExtreme)
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
                .build());
    }

    static boolean isBullishEngulfing(Candle prior, Candle signal) {
        boolean priorBear = prior.close() < prior.open();
        boolean signalBull = signal.close() > signal.open();
        boolean engulfs = signal.open() <= prior.close() && signal.close() >= prior.open();
        return priorBear && signalBull && engulfs;
    }

    static boolean isBearishEngulfing(Candle prior, Candle signal) {
        boolean priorBull = prior.close() > prior.open();
        boolean signalBear = signal.close() < signal.open();
        boolean engulfs = signal.open() >= prior.close() && signal.close() <= prior.open();
        return priorBull && signalBear && engulfs;
    }
}
