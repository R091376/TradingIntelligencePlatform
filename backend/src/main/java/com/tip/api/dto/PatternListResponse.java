package com.tip.api.dto;

import java.util.List;

public record PatternListResponse(
        String symbolId,
        String statusFilter,
        List<PatternInstanceDto> patterns
) {
}
