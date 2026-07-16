package com.tip.patterns.structure;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternDirection;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.support.ShortTradeLifecycle;

import java.time.Instant;
import java.util.List;

public final class StructureLifecycle {

    private StructureLifecycle() {
    }

    public static List<PatternStageEvent> onCandle(
            ActivePattern instance,
            Candle candle,
            StructureConfig config,
            Instant now
    ) {
        boolean longSide = instance.direction() == PatternDirection.LONG;
        return ShortTradeLifecycle.onCandle(
                instance,
                candle,
                now,
                config.maxCandlesAfterDetect(),
                "max_candles_structure",
                p -> p.patternType().isStructure(),
                c -> longSide
                        ? c.close() > instance.lookbackHigh()
                        : c.close() < instance.lookbackHigh()
        );
    }
}
