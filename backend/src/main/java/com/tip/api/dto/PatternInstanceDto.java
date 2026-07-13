package com.tip.api.dto;

import java.util.Map;

public record PatternInstanceDto(
        String id,
        String symbolId,
        String patternType,
        String timeframe,
        String direction,
        String status,
        Map<String, Boolean> flags,
        double referenceLevel,
        double atrAtDetect,
        double entryPrice,
        double stopLevel,
        double targetLevel,
        Double retestFloor,
        long detectCandleTime,
        String detectedAt,
        String confirmedAt,
        String detectorVersion,
        String confirmationModeUsed
) {
}
