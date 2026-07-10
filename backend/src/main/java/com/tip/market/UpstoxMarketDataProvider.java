package com.tip.market;

import com.tip.market.model.Candle;
import com.upstox.ApiException;
import com.upstox.api.GetHistoricalCandleResponse;
import com.upstox.api.GetIntraDayCandleResponse;
import com.upstox.api.HistoricalCandleData;
import com.upstox.api.IntraDayCandleData;
import io.swagger.client.api.HistoryV3Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class UpstoxMarketDataProvider implements MarketDataProvider {

    private static final Logger log = LoggerFactory.getLogger(UpstoxMarketDataProvider.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final HistoryV3Api historyV3Api;
    private final UpstoxFeedClient feedClient;

    public UpstoxMarketDataProvider(HistoryV3Api historyV3Api, UpstoxFeedClient feedClient) {
        this.historyV3Api = historyV3Api;
        this.feedClient = feedClient;
    }

    @Override
    public List<Candle> fetchIntradayCandles(String instrumentKey, String timeframe) {
        TimeframeParser.TimeframeSpec spec = TimeframeParser.parse(timeframe);
        try {
            GetIntraDayCandleResponse response = historyV3Api.getIntraDayCandleData(
                    instrumentKey,
                    spec.unit(),
                    spec.interval()
            );
            IntraDayCandleData data = response.getData();
            if (data == null || data.getCandles() == null) {
                return List.of();
            }
            return UpstoxCandleMapper.fromRawCandles(data.getCandles());
        } catch (ApiException e) {
            throw new UpstoxMarketDataException(
                    "Failed to fetch intraday candles for " + instrumentKey, e);
        }
    }

    @Override
    public List<Candle> fetchHistoricalCandles(
            String instrumentKey,
            String timeframe,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        TimeframeParser.TimeframeSpec spec = TimeframeParser.parse(timeframe);
        try {
            GetHistoricalCandleResponse response = historyV3Api.getHistoricalCandleData1(
                    instrumentKey,
                    spec.unit(),
                    spec.interval(),
                    toDate.format(DATE_FORMAT),
                    fromDate.format(DATE_FORMAT)
            );
            HistoricalCandleData data = response.getData();
            if (data == null || data.getCandles() == null) {
                return List.of();
            }
            return UpstoxCandleMapper.fromRawCandles(data.getCandles());
        } catch (ApiException e) {
            throw new UpstoxMarketDataException(
                    "Failed to fetch historical candles for " + instrumentKey, e);
        }
    }

    @Override
    public void connectLiveFeed(Set<String> instrumentKeys, TickHandler handler) {
        log.info("Starting Upstox live feed for {} instrument(s)", instrumentKeys.size());
        feedClient.connect(instrumentKeys, handler);
    }

    @Override
    public void subscribeInstruments(Set<String> instrumentKeys) {
        log.info("Subscribing live feed instruments: {}", instrumentKeys);
        feedClient.subscribe(instrumentKeys);
    }

    @Override
    public void unsubscribeInstruments(Set<String> instrumentKeys) {
        log.info("Unsubscribing live feed instruments: {}", instrumentKeys);
        feedClient.unsubscribe(instrumentKeys);
    }

    @Override
    public void disconnectLiveFeed() {
        feedClient.disconnect();
    }
}
