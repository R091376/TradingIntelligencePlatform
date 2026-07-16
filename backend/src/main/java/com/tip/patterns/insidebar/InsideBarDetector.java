package com.tip.patterns.insidebar;

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
 * Detects mother → inside → breakout close beyond mother range.
 * Break may occur on the bar after the inside bar, or within
 * {@link InsideBarConfig#maxBarsAfterInside()} bars after the inside bar.
 */
public final class InsideBarDetector {

    private InsideBarDetector() {
    }

    public static Optional<ActivePattern> tryDetect(
            String symbolId,
            String timeframe,
            List<Candle> closedAscending,
            boolean alreadyOpen,
            InsideBarConfig config,
            Instant now
    ) {
        if (alreadyOpen || closedAscending == null || closedAscending.size() < 3 || config == null) {
            return Optional.empty();
        }
        var atrOpt = AtrIndicator.latest(closedAscending, config.atrPeriod());
        if (atrOpt.isEmpty() || !(atrOpt.getAsDouble() > 0)) {
            return Optional.empty();
        }
        double atr = atrOpt.getAsDouble();

        int n = closedAscending.size();
        Candle signal = closedAscending.get(n - 1);
        int maxLag = Math.max(1, config.maxBarsAfterInside());

        // insideIdx is the inside bar; mother is immediately before it; signal is last bar
        for (int lag = 1; lag <= maxLag; lag++) {
            int insideIdx = n - 1 - lag;
            int motherIdx = insideIdx - 1;
            if (motherIdx < 0) {
                break;
            }
            Candle mother = closedAscending.get(motherIdx);
            Candle inside = closedAscending.get(insideIdx);

            double motherRange = mother.high() - mother.low();
            if (!(motherRange >= config.minMotherRangeAtrMult() * atr)) {
                continue;
            }
            if (!(inside.high() < mother.high() && inside.low() > mother.low())) {
                continue;
            }

            boolean longBreak = signal.close() > mother.high();
            boolean shortBreak = signal.close() < mother.low();
            if (longBreak == shortBreak) {
                continue;
            }

            // Fire only on the first break bar (previous close still inside mother range)
            if (n >= 2) {
                Candle prev = closedAscending.get(n - 2);
                if (longBreak && prev.close() > mother.high()) {
                    continue;
                }
                if (shortBreak && prev.close() < mother.low()) {
                    continue;
                }
            }

            // Prefer the most recent valid inside (smallest lag) — first match
            return Optional.of(build(
                    symbolId, timeframe, signal, mother, longBreak, atr, config, now));
        }

        return Optional.empty();
    }

    private static ActivePattern build(
            String symbolId,
            String timeframe,
            Candle signal,
            Candle mother,
            boolean longSide,
            double atr,
            InsideBarConfig config,
            Instant now
    ) {
        double entry = signal.close();
        double stop = longSide ? mother.low() : mother.high();
        double target = longSide
                ? entry + config.successAtrMult() * atr
                : entry - config.successAtrMult() * atr;
        double confirmLevel = longSide ? mother.high() : mother.low();

        return ActivePattern.builder()
                .id(UUID.randomUUID())
                .patternType(PatternType.INSIDE_BAR)
                .symbolId(symbolId)
                .timeframe(timeframe)
                .direction(longSide ? PatternDirection.LONG : PatternDirection.SHORT)
                .status(PatternStage.DETECTED)
                .confirmationModeUsed(ConfirmationMode.CLOSE)
                .referenceLevel(stop)
                .lookbackHigh(confirmLevel)
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
