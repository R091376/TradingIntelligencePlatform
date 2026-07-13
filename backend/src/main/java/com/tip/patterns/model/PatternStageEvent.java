package com.tip.patterns.model;

import java.time.Instant;
import java.util.UUID;

/**
 * One lifecycle transition for journal / WS.
 */
public record PatternStageEvent(
        UUID instanceId,
        PatternStage stage,
        long candleTime,
        double priceAtEvent,
        Instant eventTime
) {
}
