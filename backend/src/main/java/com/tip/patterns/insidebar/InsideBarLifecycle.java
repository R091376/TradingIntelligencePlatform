package com.tip.patterns.insidebar;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternDirection;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;
import com.tip.patterns.support.ShortTradeLifecycle;

import java.time.Instant;
import java.util.List;

public final class InsideBarLifecycle {

    private InsideBarLifecycle() {
    }

    public static List<PatternStageEvent> onCandle(
            ActivePattern instance,
            Candle candle,
            InsideBarConfig config,
            Instant now
    ) {
        boolean longSide = instance.direction() == PatternDirection.LONG;
        return ShortTradeLifecycle.onCandle(
                instance,
                candle,
                now,
                config.maxCandlesAfterDetect(),
                "max_candles_inside_bar",
                p -> p.patternType() == PatternType.INSIDE_BAR,
                c -> longSide
                        ? c.close() > instance.lookbackHigh()
                        : c.close() < instance.lookbackHigh()
        );
    }
}
