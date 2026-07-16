package com.tip.patterns.consolidation;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;
import com.tip.patterns.support.SimpleBarEvaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ConsolidationBarEvaluator {

    private ConsolidationBarEvaluator() {
    }

    public static SimpleBarEvaluation evaluate(
            String symbolId,
            String timeframe,
            List<ActivePattern> openConsol,
            List<Candle> closedAscending,
            ConsolidationConfig config,
            Instant now
    ) {
        SimpleBarEvaluation.Builder out = SimpleBarEvaluation.builder();
        if (closedAscending == null || closedAscending.isEmpty()) {
            for (ActivePattern p : openConsol) {
                out.trackOpenOrClosed(p);
            }
            return out.build();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        List<ActivePattern> stillOpen = new ArrayList<>();

        for (ActivePattern open : openConsol) {
            if (open.patternType() != PatternType.CONSOLIDATION || open.isTerminal()) {
                continue;
            }
            List<PatternStageEvent> stageEvents =
                    ConsolidationLifecycle.onCandle(open, signal, closedAscending, config, now);
            out.addAdvanced(open);
            out.addEvents(stageEvents);
            if (!open.isTerminal()) {
                stillOpen.add(open);
            } else {
                out.trackOpenOrClosed(open);
            }
        }

        ConsolidationDetector.tryDetect(
                symbolId, timeframe, closedAscending, !stillOpen.isEmpty(), config, now
        ).ifPresent(detected -> {
            out.addNew(detected);
            out.addEvent(new PatternStageEvent(
                    detected.id(), PatternStage.DETECTED, signal.time(), signal.close(), now));
            List<PatternStageEvent> sameBar =
                    ConsolidationLifecycle.onCandle(detected, signal, closedAscending, config, now);
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
