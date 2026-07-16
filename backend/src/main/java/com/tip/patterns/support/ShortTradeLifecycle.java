package com.tip.patterns.support;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.FinalOutcome;
import com.tip.patterns.model.PatternDirection;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared short lifecycle: Detected → Confirmed → Succeeded | Failed (+ soft expire last).
 * Used by pin-bar-style classic patterns (engulfing, inside-bar breakout, HH/LL, volume breakout).
 */
public final class ShortTradeLifecycle {

    private ShortTradeLifecycle() {
    }

    /**
     * Order: MFE/MAE → Failed → Succeeded → Confirmed → soft expire (if still open).
     */
    public static List<PatternStageEvent> onCandle(
            ActivePattern instance,
            Candle candle,
            Instant now,
            int maxCandlesAfterDetect,
            String expireReason,
            Predicate<ActivePattern> typeGuard,
            Predicate<Candle> confirmOk
    ) {
        if (instance.isTerminal()) {
            return List.of();
        }
        if (typeGuard != null && !typeGuard.test(instance)) {
            throw new IllegalArgumentException("Lifecycle type guard failed for " + instance.patternType());
        }

        List<PatternStageEvent> events = new ArrayList<>(3);
        boolean longSide = instance.direction() == PatternDirection.LONG;

        instance.setMfePrice(Math.max(instance.mfePrice(), candle.high()));
        instance.setMaePrice(Math.min(instance.maePrice(), candle.low()));
        if (candle.time() != instance.detectCandleTime()) {
            instance.setDurationCandles(instance.durationCandles() + 1);
        }

        // Failed first (prefer FAILED over EXPIRED on the same bar)
        if (longSide) {
            if (candle.close() < instance.stopLevel()) {
                instance.markTerminal(FinalOutcome.FAILED, PatternStage.FAILED, "invalidation", candle.close());
                events.add(event(instance, PatternStage.FAILED, candle, now));
                return events;
            }
        } else if (candle.close() > instance.stopLevel()) {
            instance.markTerminal(FinalOutcome.FAILED, PatternStage.FAILED, "invalidation", candle.close());
            events.add(event(instance, PatternStage.FAILED, candle, now));
            return events;
        }

        // Succeeded (not on detect bar)
        if (candle.time() != instance.detectCandleTime()) {
            boolean hitTarget = longSide
                    ? candle.high() >= instance.targetLevel()
                    : candle.low() <= instance.targetLevel();
            if (hitTarget) {
                instance.markTerminal(FinalOutcome.SUCCEEDED, PatternStage.SUCCEEDED, "price_target", candle.close());
                events.add(event(instance, PatternStage.SUCCEEDED, candle, now));
                return events;
            }
        }

        // Confirmed
        if (!instance.flagConfirmed()
                && candle.time() != instance.detectCandleTime()
                && confirmOk != null
                && confirmOk.test(candle)) {
            instance.setFlagConfirmed(true);
            instance.setConfirmedAt(now);
            events.add(event(instance, PatternStage.CONFIRMED, candle, now));
        }

        // Soft expire only if still open after fail/success/confirm
        if (instance.durationCandles() >= maxCandlesAfterDetect) {
            return com.tip.patterns.PatternLifecycleSupport.expire(
                    instance, candle, expireReason, now, false);
        }

        instance.refreshDisplayStatus();
        return events;
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
