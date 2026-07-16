package com.tip.patterns.consolidation;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.FinalOutcome;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Consolidation lifecycle: Detected → Strengthened? → Succeeded (expansion) | Failed (timeout).
 */
public final class ConsolidationLifecycle {

    private ConsolidationLifecycle() {
    }

    public static List<PatternStageEvent> onCandle(
            ActivePattern instance,
            Candle candle,
            List<Candle> closedAscending,
            ConsolidationConfig config,
            Instant now
    ) {
        if (instance.isTerminal()) {
            return List.of();
        }
        if (instance.patternType() != PatternType.CONSOLIDATION) {
            throw new IllegalArgumentException("ConsolidationLifecycle only handles CONSOLIDATION");
        }

        List<PatternStageEvent> events = new ArrayList<>(3);
        double rangeHigh = instance.lookbackHigh();
        double rangeLow = instance.referenceLevel();
        double detectRange = rangeHigh - rangeLow;

        instance.setMfePrice(Math.max(instance.mfePrice(), candle.high()));
        instance.setMaePrice(Math.min(instance.maePrice(), candle.low()));
        if (candle.time() != instance.detectCandleTime()) {
            instance.setDurationCandles(instance.durationCandles() + 1);
        }

        // Expansion success (either side) — prefer SUCCEEDED over timeout on same bar
        if (candle.close() > rangeHigh || candle.close() < rangeLow) {
            String side = candle.close() > rangeHigh ? "expand_up" : "expand_down";
            instance.markTerminal(FinalOutcome.SUCCEEDED, PatternStage.SUCCEEDED, side, candle.close());
            events.add(event(instance, PatternStage.SUCCEEDED, candle, now));
            return events;
        }

        // Timeout fail: still inside after max duration
        if (instance.durationCandles() >= config.maxDurationCandles()) {
            instance.markTerminal(FinalOutcome.FAILED, PatternStage.FAILED, "max_duration", candle.close());
            events.add(event(instance, PatternStage.FAILED, candle, now));
            return events;
        }

        // Strengthened: still inside frozen box AND (duration stretch OR rolling window tightened)
        if (!instance.flagStrengthened()
                && candle.high() <= rangeHigh
                && candle.low() >= rangeLow) {
            int minStretch = Math.max(config.windowCandles() * 3 / 2, config.windowCandles() + 1);
            boolean longEnough = instance.durationCandles() >= minStretch;

            boolean tighter = false;
            OptionalDouble rolling = rollingWindowRange(closedAscending, config.windowCandles());
            if (rolling.isPresent() && detectRange > 0) {
                tighter = rolling.getAsDouble() <= config.tightenRatio() * detectRange
                        && rolling.getAsDouble() > 0;
            }

            if (longEnough || tighter) {
                instance.setFlagStrengthened(true);
                events.add(event(instance, PatternStage.STRENGTHENED, candle, now));
            }
        }

        instance.refreshDisplayStatus();
        return events;
    }

    /** Range of the last {@code window} closed bars (high−low). */
    static OptionalDouble rollingWindowRange(List<Candle> closedAscending, int window) {
        if (closedAscending == null || window < 1 || closedAscending.size() < window) {
            return OptionalDouble.empty();
        }
        int n = closedAscending.size();
        double hi = Double.NEGATIVE_INFINITY;
        double lo = Double.POSITIVE_INFINITY;
        for (int i = n - window; i < n; i++) {
            Candle c = closedAscending.get(i);
            hi = Math.max(hi, c.high());
            lo = Math.min(lo, c.low());
        }
        double r = hi - lo;
        if (!(r > 0) || !Double.isFinite(r)) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(r);
    }

    private static PatternStageEvent event(
            ActivePattern instance,
            PatternStage stage,
            Candle candle,
            Instant now
    ) {
        return new PatternStageEvent(instance.id(), stage, candle.time(), candle.close(), now);
    }
}
