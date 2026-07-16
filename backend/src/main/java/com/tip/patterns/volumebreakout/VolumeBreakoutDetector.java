package com.tip.patterns.volumebreakout;

import com.tip.indicators.AtrIndicator;
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
 * Volume expansion bar: high volume + large range. Independent of Donchian breakout.
 * Docs: {@code docs/patterns/volume-breakout.md}
 */
public final class VolumeBreakoutDetector {

    private VolumeBreakoutDetector() {
    }

    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            boolean alreadyOpen,
            boolean indexSegment,
            VolumeBreakoutConfig config,
            Instant now
    ) {
        if (alreadyOpen || closedAscending == null || closedAscending.isEmpty() || config == null) {
            return Optional.empty();
        }
        if (indexSegment) {
            return Optional.empty();
        }

        var atrOpt = AtrIndicator.latest(closedAscending, config.atrPeriod());
        var volOpt = VolumeSmaIndicator.priorAverage(closedAscending, config.volumeAvgPeriod());
        if (atrOpt.isEmpty() || volOpt.isEmpty()) {
            return Optional.empty();
        }
        double atr = atrOpt.getAsDouble();
        double avgVol = volOpt.getAsDouble();
        if (!(atr > 0) || !(avgVol > 0)) {
            return Optional.empty();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        if (signal.volume() <= 0) {
            return Optional.empty();
        }
        double barRange = signal.high() - signal.low();
        boolean volOk = signal.volume() >= config.volumeMultiplier() * avgVol;
        boolean moveOk = barRange >= config.minRangeAtrMult() * atr;
        if (!volOk || !moveOk) {
            return Optional.empty();
        }

        boolean longSide = signal.close() >= signal.open();
        double entry = signal.close();
        double stop = longSide ? signal.low() : signal.high();
        double target = longSide
                ? entry + config.successAtrMult() * atr
                : entry - config.successAtrMult() * atr;
        double confirmExtreme = longSide ? signal.high() : signal.low();

        return Optional.of(ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(PatternType.VOLUME_BREAKOUT)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(longSide ? PatternDirection.LONG : PatternDirection.SHORT)
                .status(PatternStage.DETECTED)
                .volumeOkAtDetect(true)
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
}
