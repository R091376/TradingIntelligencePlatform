package com.tip.api.dto;

import com.tip.market.model.Candle;

public record CandleDto(
        long time,
        double open,
        double high,
        double low,
        double close,
        long volume
) {
    public static CandleDto from(Candle candle) {
        return new CandleDto(
                candle.time(),
                candle.open(),
                candle.high(),
                candle.low(),
                candle.close(),
                candle.volume()
        );
    }
}