package com.tip.market.event;

import com.tip.market.model.Candle;

public record CandleUpdatedEvent(
        String instrumentKey,
        String timeframe,
        Candle candle,
        boolean isFinal
) {
}