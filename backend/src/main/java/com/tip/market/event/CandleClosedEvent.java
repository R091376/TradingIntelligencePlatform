package com.tip.market.event;

import com.tip.market.model.Candle;

public record CandleClosedEvent(
        String instrumentKey,
        String timeframe,
        Candle candle
) {
}