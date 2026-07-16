package com.tip.patterns.insidebar;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;
import com.tip.patterns.support.SimpleBarEvaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class InsideBarBarEvaluator {

    private InsideBarBarEvaluator() {
    }

    public static SimpleBarEvaluation evaluate(
            String symbolId,
            String timeframe,
            List<ActivePattern> openInside,
            List<Candle> closedAscending,
            InsideBarConfig config,
            Instant now
    ) {
        SimpleBarEvaluation.Builder out = SimpleBarEvaluation.builder();
        if (closedAscending == null || closedAscending.isEmpty()) {
            for (ActivePattern p : openInside) {
                out.trackOpenOrClosed(p);
            }
            return out.build();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        List<ActivePattern> stillOpen = new ArrayList<>();

        for (ActivePattern open : openInside) {
            if (open.patternType() != PatternType.INSIDE_BAR || open.isTerminal()) {
                continue;
            }
            List<PatternStageEvent> stageEvents = InsideBarLifecycle.onCandle(open, signal, config, now);
            out.addAdvanced(open);
            out.addEvents(stageEvents);
            if (!open.isTerminal()) {
                stillOpen.add(open);
            } else {
                out.trackOpenOrClosed(open);
            }
        }

        InsideBarDetector.tryDetect(
                symbolId, timeframe, closedAscending, !stillOpen.isEmpty(), config, now
        ).ifPresent(detected -> {
            out.addNew(detected);
            out.addEvent(new PatternStageEvent(
                    detected.id(), PatternStage.DETECTED, signal.time(), signal.close(), now));
            List<PatternStageEvent> sameBar = InsideBarLifecycle.onCandle(detected, signal, config, now);
            out.addEvents(sameBar);
            if (detected.isTerminal()) {
                out.trackOpenOrClosed(detected);
            } else {
                stillOpen.add(detected);
            }
        });

        for (ActivePattern open : stillOpen) {
            out.trackOpenOrClosed(open);
        }
        return out.build();
    }
}
