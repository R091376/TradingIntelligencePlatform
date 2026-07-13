package com.tip.api.dto;

/**
 * WebSocket {@code pattern_event} payload (Data-Flow spec + extensions).
 */
public record PatternEventMessage(
        String type,
        String symbolId,
        String timeframe,
        String patternType,
        String stage,
        double referenceLevel,
        double price,
        long time,
        String instanceId,
        String direction,
        String status,
        double entryPrice,
        double stopLevel,
        double targetLevel,
        String confirmationModeUsed
) {
}
