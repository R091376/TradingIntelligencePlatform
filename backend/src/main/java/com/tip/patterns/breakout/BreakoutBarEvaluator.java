package com.tip.patterns.breakout;

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
 * Orchestrates Breakout advance + detect for one closed bar (pure, no Spring/DB).
 *
 * <p>Order: advance all open instances, then try new Detected (PI-6 vs remaining opens),
 * then same-bar volume confirm / terminal path on the new instance.
 */
public final class BreakoutBarEvaluator {

    private BreakoutBarEvaluator() {
    }

    /**
     * @param openBreakouts open BREAKOUT instances for this symbol×TF (mutated in place)
     * @param closedAscending closed candles including the signal bar as last
     * @param indexSegment    INDEX segment → volume fallback
     */
    public static BreakoutBarEvaluation evaluate(
            String symbolId,
            String timeframe,
            List<ActivePattern> openBreakouts,
            List<Candle> closedAscending,
            boolean indexSegment,
            BreakoutConfig config,
            Instant now
    ) {
        BreakoutBarEvaluation.Builder out = BreakoutBarEvaluation.builder();
        if (closedAscending == null || closedAscending.isEmpty()) {
            for (ActivePattern p : openBreakouts) {
                out.trackOpenOrClosed(p);
            }
            return out.build();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        List<ActivePattern> stillOpenAfterAdvance = new ArrayList<>();

        for (ActivePattern open : openBreakouts) {
            if (open.patternType() != PatternType.BREAKOUT || open.isTerminal()) {
                continue;
            }
            List<PatternStageEvent> stageEvents = BreakoutLifecycle.onCandle(open, signal, config, now);
            out.addAdvanced(open);
            out.addEvents(stageEvents);
            if (!open.isTerminal()) {
                stillOpenAfterAdvance.add(open);
            } else {
                out.trackOpenOrClosed(open);
            }
        }

        double maxOpenRef = stillOpenAfterAdvance.stream()
                .mapToDouble(ActivePattern::referenceLevel)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);

        BreakoutDetector.tryDetect(
                symbolId,
                timeframe,
                closedAscending,
                maxOpenRef,
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
            List<PatternStageEvent> sameBar = BreakoutLifecycle.onCandle(detected, signal, config, now);
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
     * Max frozen reference among open breakouts (for external callers).
     */
    public static double maxOpenReference(List<ActivePattern> openBreakouts) {
        return openBreakouts.stream()
                .filter(p -> !p.isTerminal() && p.patternType() == PatternType.BREAKOUT)
                .mapToDouble(ActivePattern::referenceLevel)
                .max()
                .orElse(Double.NEGATIVE_INFINITY);
    }

    /** Exposed for tests — volume mode confirm on detect bar. */
    static boolean isVolumeConfirmMode(ActivePattern p) {
        return p.confirmationModeUsed() == ConfirmationMode.VOLUME;
    }
}
