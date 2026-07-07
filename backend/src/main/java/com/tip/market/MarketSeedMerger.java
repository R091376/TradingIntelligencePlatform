package com.tip.market;

import com.tip.market.model.Candle;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

public final class MarketSeedMerger {

    private MarketSeedMerger() {
    }

    public static List<Candle> merge(List<Candle> historical, List<Candle> intraday) {
        TreeMap<Long, Candle> byTime = new TreeMap<>();
        for (Candle candle : historical) {
            byTime.put(candle.time(), candle);
        }
        for (Candle candle : intraday) {
            byTime.put(candle.time(), candle);
        }
        return new ArrayList<>(byTime.values());
    }
}