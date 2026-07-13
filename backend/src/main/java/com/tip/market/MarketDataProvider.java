package com.tip.market;

import com.tip.market.model.Candle;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface MarketDataProvider {

    List<Candle> fetchIntradayCandles(String instrumentKey, String timeframe);

    List<Candle> fetchHistoricalCandles(
            String instrumentKey,
            String timeframe,
            LocalDate fromDate,
            LocalDate toDate
    );

    /**
     * Connect (or ensure connected) the live feed and subscribe the given instrument keys.
     * Subsequent adds should use {@link #subscribeInstruments(Set)} without recreating the streamer.
     */
    void connectLiveFeed(Set<String> instrumentKeys, TickHandler handler);

    /**
     * Dynamically subscribe additional instruments on the existing streamer (no recreate).
     */
    void subscribeInstruments(Set<String> instrumentKeys);

    /**
     * Dynamically unsubscribe instruments from the existing streamer (no recreate).
     */
    void unsubscribeInstruments(Set<String> instrumentKeys);

    void disconnectLiveFeed();
}
