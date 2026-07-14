package com.tip.patterns.breakdown;

import com.tip.market.model.Candle;
import com.tip.patterns.PatternLifecycleSupport;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.ConfirmationMode;
import com.tip.patterns.model.FinalOutcome;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Advances an open Breakdown instance on one closed candle.
 * Math/stages: {@code docs/patterns/breakdown.md}
 */
public final class BreakdownLifecycle {

    private BreakdownLifecycle() {
    }

    /**
     * Apply one closed bar. Mutates {@code instance}. Returns stage events in order
     * (non-terminal enrichments and/or a single terminal).
     */
    public static List<PatternStageEvent> onCandle(
            ActivePattern instance,
            Candle candle,
            BreakdownConfig config,
            Instant now
    ) {
        if (instance.isTerminal()) {
            return List.of();
        }
        if (instance.patternType() != PatternType.BREAKDOWN) {
            throw new IllegalArgumentException("BreakdownLifecycle only handles BREAKDOWN");
        }

        List<PatternStageEvent> events = new ArrayList<>(4);
        double atr = instance.atrAtDetect();
        double ref = instance.referenceLevel();

        // 1) MFE / MAE extremes (max high / min low — R formulas are direction-aware)
        instance.setMfePrice(Math.max(instance.mfePrice(), candle.high()));
        instance.setMaePrice(Math.min(instance.maePrice(), candle.low()));
        if (candle.time() != instance.detectCandleTime()) {
            instance.setDurationCandles(instance.durationCandles() + 1);
        }

        // 2) Failed first (PI-21) — close back above reference
        if (candle.close() > ref) {
            instance.markTerminal(FinalOutcome.FAILED, PatternStage.FAILED, "invalidation", candle.close());
            events.add(event(instance, PatternStage.FAILED, candle, now));
            return events;
        }

        // 3) Succeeded — allowed from DETECTED without Confirmed (PI-7)
        if (candle.low() <= instance.targetLevel()) {
            instance.markTerminal(FinalOutcome.SUCCEEDED, PatternStage.SUCCEEDED, "price_target", candle.close());
            events.add(event(instance, PatternStage.SUCCEEDED, candle, now));
            return events;
        }

        // 4) Confirmed
        if (!instance.flagConfirmed() && canConfirm(instance, candle)) {
            instance.setFlagConfirmed(true);
            instance.setConfirmedAt(now);
            events.add(event(instance, PatternStage.CONFIRMED, candle, now));
        }

        // 5) Retested (once). retestFloor stores retest extreme (ceiling for short)
        if (instance.flagConfirmed() && !instance.flagRetested()) {
            double retestBand = ref - config.retestAtrMult() * atr;
            if (candle.high() >= retestBand && candle.close() <= ref) {
                instance.setFlagRetested(true);
                instance.setRetestFloor(candle.high());
                if (candle.high() > ref) {
                    double risk = candle.high() - ref;
                    instance.setTargetLevel(ref - config.successRr() * risk);
                }
                events.add(event(instance, PatternStage.RETESTED, candle, now));
            }
        }

        // 6) Strengthened (once; independent of retested)
        if (instance.flagConfirmed() && !instance.flagStrengthened()) {
            if (candle.low() <= ref - config.strengthenAtrMult() * atr) {
                instance.setFlagStrengthened(true);
                events.add(event(instance, PatternStage.STRENGTHENED, candle, now));
            }
        }

        instance.refreshDisplayStatus();
        return events;
    }

    /**
     * Force expire — delegates to shared support (type-agnostic).
     */
    public static List<PatternStageEvent> expire(
            ActivePattern instance,
            Candle lastCandleOrNull,
            String reason,
            Instant now,
            boolean nullExcursions
    ) {
        return PatternLifecycleSupport.expire(instance, lastCandleOrNull, reason, now, nullExcursions);
    }

    static boolean canConfirm(ActivePattern instance, Candle candle) {
        ConfirmationMode mode = instance.confirmationModeUsed();
        boolean stillBelow = candle.close() < instance.referenceLevel();
        if (!stillBelow) {
            return false;
        }
        boolean detectBar = candle.time() == instance.detectCandleTime();
        return switch (mode) {
            case VOLUME -> instance.volumeOkAtDetect() && detectBar;
            case CLOSE, CLOSE_FALLBACK -> !detectBar;
            case BOTH -> instance.volumeOkAtDetect() && !detectBar;
        };
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
