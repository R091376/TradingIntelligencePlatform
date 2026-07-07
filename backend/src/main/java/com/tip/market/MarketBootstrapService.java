package com.tip.market;

import com.tip.config.MarketProperties;
import com.tip.config.UpstoxProperties;
import com.tip.market.model.Candle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class MarketBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(MarketBootstrapService.class);

    private final MarketDataProvider marketDataProvider;
    private final CandleEngine candleEngine;
    private final MarketProperties marketProperties;
    private final UpstoxProperties upstoxProperties;
    private final MarketStatusService marketStatusService;

    @Value("${tip.market.live-feed-enabled:true}")
    private boolean liveFeedEnabled;

    public MarketBootstrapService(
            MarketDataProvider marketDataProvider,
            CandleEngine candleEngine,
            MarketProperties marketProperties,
            UpstoxProperties upstoxProperties,
            MarketStatusService marketStatusService
    ) {
        this.marketDataProvider = marketDataProvider;
        this.candleEngine = candleEngine;
        this.marketProperties = marketProperties;
        this.upstoxProperties = upstoxProperties;
        this.marketStatusService = marketStatusService;
    }

    public void recoverSession() {
        marketStatusService.refreshPhaseFromClock();

        if (upstoxProperties.accessToken() == null || upstoxProperties.accessToken().isBlank()) {
            marketStatusService.setBootstrapFailed(
                    "UPSTOX_ACCESS_TOKEN is not configured. Add it to your .env file.");
            log.warn("Session recovery skipped — UPSTOX_ACCESS_TOKEN is not set");
            return;
        }

        String instrumentKey = marketProperties.defaultInstrumentKey();
        String timeframe = marketProperties.defaultTimeframe();
        String symbol = marketProperties.defaultSymbol();

        marketStatusService.setBootstrapPending();
        log.info("Session recovery: fetching {} {} candles for {}", timeframe, symbol, instrumentKey);

        try {
            List<Candle> seedCandles = loadSeedCandles(instrumentKey, timeframe);
            candleEngine.seed(instrumentKey, timeframe, seedCandles);
            marketStatusService.setBootstrapReady(seedCandles.size());

            log.info("Session recovery complete: {} candles ({} closed, in-progress={})",
                    seedCandles.size(),
                    candleEngine.getClosedCandles(instrumentKey, timeframe).size(),
                    candleEngine.getCurrentCandle(instrumentKey, timeframe).isPresent());

            connectLiveFeedIfEnabled(instrumentKey, timeframe);
        } catch (UpstoxMarketDataException e) {
            String message = toUserFriendlyError(e);
            marketStatusService.setBootstrapFailed(message);
            log.error("Session recovery failed: {}", message);
        }
    }

    private void connectLiveFeedIfEnabled(String instrumentKey, String timeframe) {
        if (!liveFeedEnabled) {
            log.info("Live feed disabled (tip.market.live-feed-enabled=false)");
            marketStatusService.setLiveFeedConnected(false);
            return;
        }

        marketDataProvider.connectLiveFeed(
                instrumentKey,
                tick -> candleEngine.processTick(tick, timeframe)
        );
    }

    private List<Candle> loadSeedCandles(String instrumentKey, String timeframe) {
        List<Candle> intraday = marketDataProvider.fetchIntradayCandles(instrumentKey, timeframe);
        logCandleSummary("Intraday (today)", intraday);

        LocalDate toDate = LocalDate.now(CandleBoundaryUtils.NSE_ZONE).minusDays(1);
        LocalDate fromDate = toDate.minusDays(5);
        List<Candle> historical = marketDataProvider.fetchHistoricalCandles(
                instrumentKey, timeframe, fromDate, toDate);
        logCandleSummary("Historical", historical);

        return MarketSeedMerger.merge(historical, intraday);
    }

    private String toUserFriendlyError(UpstoxMarketDataException e) {
        String details = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
        if (details == null) {
            return "Failed to fetch market data from Upstox.";
        }
        if (details.contains("401") || details.contains("403") || details.contains("Unauthorized")) {
            return "Upstox access token is invalid or expired. Update UPSTOX_ACCESS_TOKEN in .env.";
        }
        if (details.contains("timeout") || details.contains("Timed out") || details.contains("Connection")) {
            return "Could not reach Upstox. Check your network connection and try again.";
        }
        return "Failed to fetch market data from Upstox: " + details;
    }

    private void logCandleSummary(String label, List<Candle> candles) {
        if (candles.isEmpty()) {
            log.warn("{} candles: none returned", label);
            return;
        }

        Candle first = candles.get(0);
        Candle last = candles.get(candles.size() - 1);
        log.info("{} candles: count={}, first=[time={} C={}], last=[time={} C={}]",
                label,
                candles.size(),
                first.time(), first.close(),
                last.time(), last.close());
    }
}