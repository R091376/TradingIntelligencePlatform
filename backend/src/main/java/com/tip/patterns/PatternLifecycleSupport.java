package com.tip.patterns;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.FinalOutcome;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;

import java.time.Instant;
import java.util.List;

/**
 * Shared terminal helpers for all pattern types (expire is type-agnostic).
 */
public final class PatternLifecycleSupport {

    private PatternLifecycleSupport() {
    }

    /**
     * Force expire (session / startup / remove / max duration). Caller supplies reason code.
     *
     * @param nullExcursions when true, journal must NULL MFE/MAE/move (PI-23)
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
            instance.markTerminal(FinalOutcome.EXPIRED, PatternStage.EXPIRED, reason, null, true);
        } else {
            instance.markTerminal(FinalOutcome.EXPIRED, PatternStage.EXPIRED, reason, end, false);
        }
        long candleTime = lastCandleOrNull != null ? lastCandleOrNull.time() : instance.detectCandleTime();
        double price = end != null ? end : instance.entryPrice();
        return List.of(new PatternStageEvent(instance.id(), PatternStage.EXPIRED, candleTime, price, now));
    }
}
