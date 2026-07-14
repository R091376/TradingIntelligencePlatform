package com.tip.patterns.breakdown;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.ConfirmationMode;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrates Breakdown advance + detect for one closed bar (pure, no Spring/DB).
 *
 * <p>Order: advance all open instances, then try new Detected (PI-6 vs remaining opens),
 * then same-bar volume confirm / terminal path on the new instance.
 */
public final class BreakdownBarEvaluator {

    private BreakdownBarEvaluator() {
    }

    /**
     * @param openBreakdowns open BREAKDOWN instances for this symbol×TF (mutated in place)
     * @param closedAscending closed candles including the signal bar as last
     * @param indexSegment    INDEX segment → volume fallback
     */
    public static BreakdownBarEvaluation evaluate(
            String symbolId,
            String timeframe,
            List<ActivePattern> openBreakdowns,
            List<Candle> closedAscending,
            boolean indexSegment,
            BreakdownConfig config,
            Instant now
    ) {
        BreakdownBarEvaluation.Builder out = BreakdownBarEvaluation.builder();
        if (closedAscending == null || closedAscending.isEmpty()) {
            for (ActivePattern p : openBreakdowns) {
                out.trackOpenOrClosed(p);
            }
            return out.build();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        List<ActivePattern> stillOpenAfterAdvance = new ArrayList<>();

        for (ActivePattern open : openBreakdowns) {
            if (open.patternType() != PatternType.BREAKDOWN || open.isTerminal()) {
                continue;
            }
            List<PatternStageEvent> stageEvents = BreakdownLifecycle.onCandle(open, signal, config, now);
            out.addAdvanced(open);
            out.addEvents(stageEvents);
            if (!open.isTerminal()) {
                stillOpenAfterAdvance.add(open);
            } else {
                out.trackOpenOrClosed(open);
            }
        }

        double minOpenRef = stillOpenAfterAdvance.stream()
                .mapToDouble(ActivePattern::referenceLevel)
                .min()
                .orElse(Double.POSITIVE_INFINITY);

        BreakdownDetector.tryDetect(
                symbolId,
                timeframe,
                closedAscending,
                minOpenRef,
                indexSegment,
                config,
                now
        ).ifPresent(detected -> {
            out.addNew(detected);
            out.addEvent(new PatternStageEvent(
                    detected.id(),
                    PatternStage.DETECTED,
                    signal.time(),
                    signal.close(),
                    now
            ));

            // Same-bar: volume-only confirm and/or succeed on detect bar
            List<PatternStageEvent> sameBar = BreakdownLifecycle.onCandle(detected, signal, config, now);
            out.addEvents(sameBar);
            if (detected.isTerminal()) {
                out.trackOpenOrClosed(detected);
            } else {
                stillOpenAfterAdvance.add(detected);
            }
        });

        for (ActivePattern open : stillOpenAfterAdvance) {
            out.trackOpenOrClosed(open);
        }

        return out.build();
    }

    /**
     * Min frozen reference among open breakdowns (for external callers).
     */
    public static double minOpenReference(List<ActivePattern> openBreakdowns) {
        return openBreakdowns.stream()
                .filter(p -> !p.isTerminal() && p.patternType() == PatternType.BREAKDOWN)
                .mapToDouble(ActivePattern::referenceLevel)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
    }

    /** Exposed for tests — volume mode confirm on detect bar. */
    static boolean isVolumeConfirmMode(ActivePattern p) {
        return p.confirmationModeUsed() == ConfirmationMode.VOLUME;
    }
}
