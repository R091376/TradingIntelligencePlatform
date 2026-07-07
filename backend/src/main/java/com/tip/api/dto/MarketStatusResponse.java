package com.tip.api.dto;

import com.tip.market.BootstrapStatus;
import com.tip.market.MarketPhase;

public record MarketStatusResponse(
        MarketPhase marketPhase,
        BootstrapStatus bootstrapStatus,
        String bootstrapError,
        String lastSeededAt,
        boolean liveFeedConnected,
        int candleCount
) {
}