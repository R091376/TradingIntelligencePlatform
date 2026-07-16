package com.tip.patterns.volumebreakout;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;
import com.tip.patterns.support.SimpleBarEvaluation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class VolumeBreakoutBarEvaluator {

    private VolumeBreakoutBarEvaluator() {
    }

    public static SimpleBarEvaluation evaluate(
            String symbolId,
            String timeframe,
            List<ActivePattern> openVolBreakouts,
            List<Candle> closedAscending,
            boolean indexSegment,
            VolumeBreakoutConfig config,
            Instant now
    ) {
        SimpleBarEvaluation.Builder out = SimpleBarEvaluation.builder();
        if (closedAscending == null || closedAscending.isEmpty()) {
            for (ActivePattern p : openVolBreakouts) {
                out.trackOpenOrClosed(p);
            }
            return out.build();
        }

        Candle signal = closedAscending.get(closedAscending.size() - 1);
        List<ActivePattern> stillOpen = new ArrayList<>();

        for (ActivePattern open : openVolBreakouts) {
            if (open.patternType() != PatternType.VOLUME_BREAKOUT || open.isTerminal()) {
                continue;
            }
            List<PatternStageEvent> stageEvents =
                    VolumeBreakoutLifecycle.onCandle(open, signal, config, now);
            out.addAdvanced(open);
            out.addEvents(stageEvents);
            if (!open.isTerminal()) {
                stillOpen.add(open);
            } else {
                out.trackOpenOrClosed(open);
            }
        }

        VolumeBreakoutDetector.tryDetect(
                symbolId, timeframe, closedAscending, !stillOpen.isEmpty(), indexSegment, config, now
        ).ifPresent(detected -> {
            out.addNew(detected);
            out.addEvent(new PatternStageEvent(
                    detected.id(), PatternStage.DETECTED, signal.time(), signal.close(), now));
            List<PatternStageEvent> sameBar =
                    VolumeBreakoutLifecycle.onCandle(detected, signal, config, now);
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
