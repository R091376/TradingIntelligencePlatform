package com.tip.patterns.breakout;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.ConfirmationMode;
import com.tip.patterns.model.FinalOutcome;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Advances an open Breakout instance on one closed candle.
 * Math/stages: {@code docs/patterns/breakout.md}
 */
public final class BreakoutLifecycle {

    private BreakoutLifecycle() {
    }

    /**
     * Apply one closed bar. Mutates {@code instance}. Returns stage events in order
     * (non-terminal enrichments and/or a single terminal).
     */
    public static List<PatternStageEvent> onCandle(
            ActivePattern instance,
            Candle candle,
            BreakoutConfig config,
            Instant now
    ) {
        if (instance.isTerminal()) {
            return List.of();
        }
        if (instance.patternType() != com.tip.patterns.model.PatternType.BREAKOUT) {
            throw new IllegalArgumentException("BreakoutLifecycle only handles BREAKOUT");
        }

        List<PatternStageEvent> events = new ArrayList<>(4);
        double atr = instance.atrAtDetect();
        double ref = instance.referenceLevel();

        // 1) MFE / MAE
        instance.setMfePrice(Math.max(instance.mfePrice(), candle.high()));
        instance.setMaePrice(Math.min(instance.maePrice(), candle.low()));
        if (candle.time() != instance.detectCandleTime()) {
            instance.setDurationCandles(instance.durationCandles() + 1);
        }

        // 2) Failed first (PI-21)
        if (candle.close() < ref) {
            instance.markTerminal(FinalOutcome.FAILED, PatternStage.FAILED, "invalidation", candle.close());
            events.add(event(instance, PatternStage.FAILED, candle, now));
            return events;
        }

        // 3) Succeeded — allowed from DETECTED without Confirmed (PI-7)
        if (candle.high() >= instance.targetLevel()) {
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

        // 5) Retested (once; may run after strengthen for flag only — status never demotes)
        if (instance.flagConfirmed() && !instance.flagRetested()) {
            double retestBand = ref + config.retestAtrMult() * atr;
            if (candle.low() <= retestBand && candle.close() >= ref) {
                instance.setFlagRetested(true);
                instance.setRetestFloor(candle.low());
                if (candle.low() < ref) {
                    double risk = ref - candle.low();
                    instance.setTargetLevel(ref + config.successRr() * risk);
                }
                events.add(event(instance, PatternStage.RETESTED, candle, now));
            }
        }

        // 6) Strengthened (once; independent of retested)
        if (instance.flagConfirmed() && !instance.flagStrengthened()) {
            if (candle.high() >= ref + config.strengthenAtrMult() * atr) {
                instance.setFlagStrengthened(true);
                events.add(event(instance, PatternStage.STRENGTHENED, candle, now));
            }
        }

        instance.refreshDisplayStatus();
        return events;
    }

    /**
     * Force expire (session / startup / remove). Caller supplies reason code.
     */
    public static List<PatternStageEvent> expire(
            ActivePattern instance,
            Candle lastCandleOrNull,
            String reason,
            Instant now,
            boolean nullExcursions
    ) {
        if (instance.isTerminal()) {
            return List.of();
        }
        Double end = lastCandleOrNull != null ? lastCandleOrNull.close() : null;
        if (nullExcursions) {
            // Journal must NULL MFE/MAE/move (PI-23); do not poison averages with 0R.
            instance.markTerminal(FinalOutcome.EXPIRED, PatternStage.EXPIRED, reason, null, true);
        } else {
            instance.markTerminal(FinalOutcome.EXPIRED, PatternStage.EXPIRED, reason, end, false);
        }
        long candleTime = lastCandleOrNull != null ? lastCandleOrNull.time() : instance.detectCandleTime();
        double price = end != null ? end : instance.entryPrice();
        return List.of(new PatternStageEvent(instance.id(), PatternStage.EXPIRED, candleTime, price, now));
    }

    static boolean canConfirm(ActivePattern instance, Candle candle) {
        ConfirmationMode mode = instance.confirmationModeUsed();
        boolean stillAbove = candle.close() > instance.referenceLevel();
        if (!stillAbove) {
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
        double price = stage == PatternStage.FAILED || stage == PatternStage.SUCCEEDED
                ? candle.close()
                : candle.close();
        return new PatternStageEvent(instance.id(), stage, candle.time(), price, now);
    }
}
