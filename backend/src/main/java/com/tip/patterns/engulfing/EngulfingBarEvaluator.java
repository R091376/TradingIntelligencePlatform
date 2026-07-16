package com.tip.patterns.engulfing;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.support.SimpleBarEvaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class EngulfingBarEvaluator {

    private EngulfingBarEvaluator() {
    }

    public static SimpleBarEvaluation evaluate(
            String symbolId,
            String timeframe,
            List<ActivePattern> openEngulfing,
            List<Candle> closedAscending,
            EngulfingConfig config,
            Instant now
    ) {
        SimpleBarEvaluation.Builder out = SimpleBarEvaluation.builder();
        if (closedAscending == null || closedAscending.isEmpty()) {
            for (ActivePattern p : openEngulfing) {
                out.trackOpenOrClosed(p);
            }
            return out.build();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        List<ActivePattern> stillOpen = new ArrayList<>();

        for (ActivePattern open : openEngulfing) {
            if (!open.patternType().isEngulfing() || open.isTerminal()) {
                continue;
            }
            List<PatternStageEvent> stageEvents = EngulfingLifecycle.onCandle(open, signal, config, now);
            out.addAdvanced(open);
            out.addEvents(stageEvents);
            if (!open.isTerminal()) {
                stillOpen.add(open);
            } else {
                out.trackOpenOrClosed(open);
            }
        }

        EngulfingDetector.tryDetect(
                symbolId, timeframe, closedAscending, !stillOpen.isEmpty(), config, now
        ).ifPresent(detected -> {
            out.addNew(detected);
            out.addEvent(new PatternStageEvent(
                    detected.id(), PatternStage.DETECTED, signal.time(), signal.close(), now));
            List<PatternStageEvent> sameBar = EngulfingLifecycle.onCandle(detected, signal, config, now);
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
