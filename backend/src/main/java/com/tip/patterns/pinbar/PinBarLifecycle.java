package com.tip.patterns.pinbar;

import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.support.ShortTradeLifecycle;

import java.time.Instant;
import java.util.List;

/**
 * Short lifecycle: Detected → Confirmed → Succeeded | Failed.
 * Docs: {@code docs/patterns/hammer-shooting-star.md}
 */
public final class PinBarLifecycle {

    private PinBarLifecycle() {
    }

    public static List<PatternStageEvent> onCandle(
            ActivePattern instance,
            Candle candle,
            PinBarConfig config,
            Instant now
    ) {
        boolean longSide = instance.patternType() == com.tip.patterns.model.PatternType.HAMMER;
        return ShortTradeLifecycle.onCandle(
                instance,
                candle,
                now,
                config.maxCandlesAfterDetect(),
                "max_candles_pinbar",
                p -> p.patternType().isPinBar(),
                c -> longSide
                        ? c.close() > instance.lookbackHigh()
                        : c.close() < instance.lookbackHigh()
        );
    }
}
