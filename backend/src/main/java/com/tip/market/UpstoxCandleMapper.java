package com.tip.market;

import com.tip.market.model.Candle;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class UpstoxCandleMapper {

    private UpstoxCandleMapper() {
    }

    public static List<Candle> fromRawCandles(List<List<Object>> rawCandles) {
        if (rawCandles == null || rawCandles.isEmpty()) {
            return List.of();
        }

        List<Candle> candles = new ArrayList<>(rawCandles.size());
        for (List<Object> raw : rawCandles) {
            if (raw == null || raw.size() < 6) {
                continue;
            }
            candles.add(new Candle(
                    parseTimestamp(raw.get(0)),
                    toDouble(raw.get(1)),
                    toDouble(raw.get(2)),
                    toDouble(raw.get(3)),
                    toDouble(raw.get(4)),
                    toLong(raw.get(5))
            ));
        }

        candles.sort(Comparator.comparingLong(Candle::time));
        return Collections.unmodifiableList(candles);
    }

    private static long parseTimestamp(Object value) {
        return OffsetDateTime.parse(value.toString()).toEpochSecond();
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }
}