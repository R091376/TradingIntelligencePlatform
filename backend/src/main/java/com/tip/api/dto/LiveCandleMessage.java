package com.tip.api.dto;

public record LiveCandleMessage(
        String type,
        String symbolId,
        String timeframe,
        CandleDto candle,
        boolean isFinal
) {
    public static LiveCandleMessage update(String symbolId, String timeframe, CandleDto candle) {
        return new LiveCandleMessage("candle_update", symbolId, timeframe, candle, false);
    }

    public static LiveCandleMessage closed(String symbolId, String timeframe, CandleDto candle) {
        return new LiveCandleMessage("candle_closed", symbolId, timeframe, candle, true);
    }
}