package com.tip.patterns.pinbar;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Advance open pin-bars, then try new detect. Pure, no Spring/DB.
 */
public final class PinBarBarEvaluator {

    private PinBarBarEvaluator() {
    }

    public static PinBarBarEvaluation evaluate(
            String symbolId,
            String timeframe,
            List<ActivePattern> openPinBars,
            List<Candle> closedAscending,
            PinBarConfig config,
            Instant now
    ) {
        PinBarBarEvaluation.Builder out = PinBarBarEvaluation.builder();
        if (closedAscending == null || closedAscending.isEmpty()) {
            for (ActivePattern p : openPinBars) {
                out.trackOpenOrClosed(p);
            }
            return out.build();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        List<ActivePattern> stillOpenAfterAdvance = new ArrayList<>();

        for (ActivePattern open : openPinBars) {
            if (!open.patternType().isPinBar() || open.isTerminal()) {
                continue;
            }
            List<PatternStageEvent> stageEvents = PinBarLifecycle.onCandle(open, signal, config, now);
            out.addAdvanced(open);
            out.addEvents(stageEvents);
            if (!open.isTerminal()) {
                stillOpenAfterAdvance.add(open);
            } else {
                out.trackOpenOrClosed(open);
            }
        }

        boolean alreadyOpen = !stillOpenAfterAdvance.isEmpty();
        PinBarDetector.tryDetect(
                symbolId,
                timeframe,
                closedAscending,
                alreadyOpen,
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
            // Detect bar: no confirm/success on same print (lifecycle no-ops for those)
            List<PatternStageEvent> sameBar = PinBarLifecycle.onCandle(detected, signal, config, now);
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
}
