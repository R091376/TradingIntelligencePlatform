package com.tip.market;

import com.tip.market.model.Candle;

import java.time.LocalDate;
import java.util.List;

public interface MarketDataProvider {

    List<Candle> fetchIntradayCandles(String instrumentKey, String timeframe);

    List<Candle> fetchHistoricalCandles(
            String instrumentKey,
            String timeframe,
            LocalDate fromDate,
            LocalDate toDate
    );

    void connectLiveFeed(String instrumentKey, TickHandler handler);

    void disconnectLiveFeed();
}