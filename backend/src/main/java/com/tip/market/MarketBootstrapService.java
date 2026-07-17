package com.tip.market;

import com.tip.config.MarketProperties;
import com.tip.config.UpstoxProperties;
import com.tip.market.model.Candle;
import com.tip.watchlist.SymbolBootstrapStatus;
import com.tip.watchlist.WatchlistEntry;
import com.tip.watchlist.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MarketBootstrapService {

    private static final Logger log = LoggerFactory.getLogger(MarketBootstrapService.class);

    private final MarketDataProvider marketDataProvider;
    private final CandleEngine candleEngine;
    private final MarketProperties marketProperties;
    private final UpstoxProperties upstoxProperties;
    private final MarketStatusService marketStatusService;
    private final WatchlistRepository watchlistRepository;

    @Value("${tip.market.live-feed-enabled:true}")
    private boolean liveFeedEnabled;

    public MarketBootstrapService(
            MarketDataProvider marketDataProvider,
            CandleEngine candleEngine,
            MarketProperties marketProperties,
            UpstoxProperties upstoxProperties,
            MarketStatusService marketStatusService,
            WatchlistRepository watchlistRepository
    ) {
        this.marketDataProvider = marketDataProvider;
        this.candleEngine = candleEngine;
        this.marketProperties = marketProperties;
        this.upstoxProperties = upstoxProperties;
        this.marketStatusService = marketStatusService;
        this.watchlistRepository = watchlistRepository;
    }

    /**
     * Bootstrap all public-active watchlist symbols sequentially (all TFs each),
     * then connect the live feed once with the union of active keys.
     * Global READY if ≥1 symbol READY; FAILED if zero.
     * <p>
     * Sets process bootstrap to PENDING at entry so HTTP clients never see a premature
     * READY while multi-symbol recovery is in flight (HTTP can start before ApplicationRunner ends).
     * Chart shim still gates on primary entry status in {@code MarketController}.
     */
    public void recoverAllActive() {
        // Hold PENDING until this method reaches a terminal READY/FAILED.
        marketStatusService.setBootstrapPending();
        marketStatusService.refreshPhaseFromClock();

        if (upstoxProperties.accessToken() == null || upstoxProperties.accessToken().isBlank()) {
            marketStatusService.setBootstrapFailed(
                    "UPSTOX_ACCESS_TOKEN is not configured. Add it to your .env file.");
            log.warn("Session recovery skipped — UPSTOX_ACCESS_TOKEN is not set");
            return;
        }

        List<WatchlistEntry> active = watchlistRepository.findAllActive();
        if (active.isEmpty()) {
            marketStatusService.setBootstrapFailed(
                    "No symbols on watchlist to bootstrap. Check seed configuration and instrument master.");
            log.warn("Session recovery skipped — watchlist is empty");
            return;
        }

        int readyCount = 0;
        int totalCandles = 0;
        int n = active.size();
        int i = 0;

        for (WatchlistEntry entry : active) {
            i++;
            log.info("Session recovery: symbol {}/{} {} ({})",
                    i, n, entry.tradingSymbol(), entry.symbolId());
            BootstrapSymbolResult result = bootstrapSymbol(entry);
            if (result.status() == SymbolBootstrapStatus.READY) {
                readyCount++;
                totalCandles += result.seededCandleCount();
            }
        }

        if (readyCount >= 1) {
            marketStatusService.setBootstrapReady(totalCandles);
            log.info("Session recovery complete: {}/{} symbols READY, total seeded candles≈{}",
                    readyCount, n, totalCandles);
            ensureLiveFeedConnected();
        } else {
            String message = "Failed to seed any watchlist symbol. Check Upstox token and network.";
            marketStatusService.setBootstrapFailed(message);
            log.error("Session recovery failed: zero symbols READY of {}", n);
        }
    }

    /**
     * Connect the live feed if needed, or subscribe any missing active keys.
     * Safe to call after a single-symbol add when recovery never started the streamer
     * (zero-READY initial bootstrap left {@code streamer == null}).
     */
    public void ensureLiveFeedConnected() {
        log.info("ensureLiveFeedConnected: ensuring Upstox streamer for active watchlist");
        connectLiveFeedForActive();
    }

    /**
     * Seed all supported timeframes for one watchlist entry sequentially.
     * Cooperative cancel: before each TF and before writing READY/FAILED, abort if
     * {@link WatchlistRepository#findBySymbolId} is empty or status is REMOVING.
     * <p>
     * Per-symbol: READY if ≥1 TF seeded; FAILED if 0.
     */
    public BootstrapSymbolResult bootstrapSymbol(WatchlistEntry entry) {
        String symbolId = entry.symbolId();
        List<String> timeframes = marketProperties.supportedTimeframes();
        int seededTfCount = 0;
        int candleCount = 0;
        List<String> errors = new ArrayList<>();

        // Ensure PENDING while seeding (preserve other fields)
        if (!isCancelled(symbolId)) {
            updateBootstrapStatus(symbolId, SymbolBootstrapStatus.PENDING, null);
        }

        for (String timeframe : timeframes) {
            if (isCancelled(symbolId)) {
                log.info("Bootstrap cancelled for {} before TF {}", symbolId, timeframe);
                return BootstrapSymbolResult.cancelled(symbolId);
            }
            try {
                List<Candle> seedCandles = loadSeedCandles(symbolId, timeframe);
                candleEngine.seed(symbolId, timeframe, seedCandles);
                // Empty history+intraday is not a successful TF seed (avoids READY with zero candles).
                if (seedCandles.isEmpty()) {
                    errors.add(timeframe + ": empty seed (no candles)");
                    log.warn("Empty seed for {} {} — not counted as successful TF",
                            symbolId, timeframe);
                    continue;
                }
                seededTfCount++;
                candleCount += seedCandles.size();
                log.info("Seeded {} {} candles for {} (count={})",
                        timeframe, entry.tradingSymbol(), symbolId, seedCandles.size());
            } catch (UpstoxMarketDataException e) {
                String message = toUserFriendlyError(e);
                errors.add(timeframe + ": " + message);
                log.error("Seed failed for {} {}: {}", symbolId, timeframe, message);
            } catch (Exception e) {
                String message = e.getMessage() != null ? e.getMessage() : e.toString();
                errors.add(timeframe + ": " + message);
                log.error("Seed failed for {} {}: {}", symbolId, timeframe, message);
            }
        }

        if (isCancelled(symbolId)) {
            log.info("Bootstrap cancelled for {} before status write", symbolId);
            return BootstrapSymbolResult.cancelled(symbolId);
        }

        if (seededTfCount >= 1) {
            updateBootstrapStatus(symbolId, SymbolBootstrapStatus.READY, null);
            log.info("Symbol READY: {} ({} TF(s) seeded, candles≈{})",
                    entry.tradingSymbol(), seededTfCount, candleCount);
            return BootstrapSymbolResult.ready(symbolId, seededTfCount, candleCount);
        }

        String error = errors.isEmpty()
                ? "No timeframes seeded with candles"
                : String.join("; ", errors);
        updateBootstrapStatus(symbolId, SymbolBootstrapStatus.FAILED, error);
        log.error("Symbol FAILED: {} — {}", entry.tradingSymbol(), error);
        return BootstrapSymbolResult.failed(symbolId, error);
    }

    private void connectLiveFeedForActive() {
        if (!liveFeedEnabled) {
            log.info("Live feed disabled (tip.market.live-feed-enabled=false)");
            marketStatusService.setLiveFeedConnected(false);
            return;
        }

        // Prefer all public-active keys (including FAILED) so ticks can still arrive post seed issues.
        Set<String> keys = watchlistRepository.findAllActive().stream()
                .map(WatchlistEntry::symbolId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (keys.isEmpty()) {
            log.warn("No active instrument keys for live feed");
            return;
        }

        log.info("Connecting/ensuring live feed for {} instrument key(s)", keys.size());
        List<String> timeframes = marketProperties.supportedTimeframes();
        marketDataProvider.connectLiveFeed(keys, tick -> {
            // After cash session close (or pre-open), do not open new live bars from residual ticks.
            // Seeded history must remain the chart source of truth for "last available" data.
            if (marketStatusService.getMarketPhase() != MarketPhase.OPEN) {
                return;
            }
            for (String timeframe : timeframes) {
                try {
                    candleEngine.processTick(tick, timeframe);
                } catch (Exception e) {
                    log.warn("processTick failed for {} {}: {}",
                            tick.instrumentKey(), timeframe, e.toString());
                }
            }
        });
    }

    private boolean isCancelled(String symbolId) {
        Optional<WatchlistEntry> current = watchlistRepository.findBySymbolId(symbolId);
        if (current.isEmpty()) {
            return true;
        }
        return current.get().bootstrapStatus() == SymbolBootstrapStatus.REMOVING;
    }

    private void updateBootstrapStatus(String symbolId, SymbolBootstrapStatus status, String error) {
        Optional<WatchlistEntry> current = watchlistRepository.findBySymbolId(symbolId);
        if (current.isEmpty()) {
            return;
        }
        WatchlistEntry e = current.get();
        if (e.bootstrapStatus() == SymbolBootstrapStatus.REMOVING) {
            return;
        }
        watchlistRepository.save(new WatchlistEntry(
                e.symbolId(),
                e.tradingSymbol(),
                e.exchange(),
                e.segment(),
                e.instrumentType(),
                e.displayName(),
                e.addedAt(),
                e.active(),
                status,
                error
        ));
    }

    private List<Candle> loadSeedCandles(String instrumentKey, String timeframe) {
        List<Candle> intraday = marketDataProvider.fetchIntradayCandles(instrumentKey, timeframe);
        logCandleSummary("Intraday (today) " + timeframe, intraday);

        // Historical window: [today - lookback, yesterday]. Today is covered by intraday.
        // Previously hardcoded minusDays(5) ≈ 3 trading days of history — not multi-symbol related.
        LocalDate toDate = LocalDate.now(CandleBoundaryUtils.NSE_ZONE).minusDays(1);
        int lookbackDays = marketProperties.lookbackDaysFor(timeframe);
        LocalDate fromDate = toDate.minusDays(Math.max(1, lookbackDays) - 1L);
        if (fromDate.isAfter(toDate)) {
            fromDate = toDate;
        }

        List<Candle> historical = fetchHistoricalChunked(instrumentKey, timeframe, fromDate, toDate);
        logCandleSummary(
                "Historical " + timeframe + " [" + fromDate + " → " + toDate + "]",
                historical);

        return MarketSeedMerger.merge(historical, intraday);
    }

    /**
     * Upstox often caps candles per historical response (~1000–1500). Walk the
     * date range in chunks and merge (dedupe by timestamp).
     */
    private List<Candle> fetchHistoricalChunked(
            String instrumentKey,
            String timeframe,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        int chunkDays = Math.max(1, marketProperties.historicalChunkDaysFor(timeframe));
        List<Candle> all = new ArrayList<>();
        LocalDate chunkEnd = toDate;
        int chunks = 0;
        while (!chunkEnd.isBefore(fromDate)) {
            LocalDate chunkStart = chunkEnd.minusDays(chunkDays - 1L);
            if (chunkStart.isBefore(fromDate)) {
                chunkStart = fromDate;
            }
            List<Candle> chunk = marketDataProvider.fetchHistoricalCandles(
                    instrumentKey, timeframe, chunkStart, chunkEnd);
            all.addAll(chunk);
            chunks++;
            log.debug(
                    "Historical chunk {} {} {}→{} candles={}",
                    timeframe, instrumentKey, chunkStart, chunkEnd, chunk.size());
            if (chunkStart.equals(fromDate)) {
                break;
            }
            chunkEnd = chunkStart.minusDays(1);
        }
        if (chunks > 1) {
            log.debug(
                    "Historical {} for {}: {} chunk(s), {} raw candles before merge",
                    timeframe, instrumentKey, chunks, all.size());
        }
        return all;
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

    /**
     * Seed fetch summary. Default (tip.market.seed-log-detail=false): counts only.
     * Detail mode also logs first/last bar samples for debugging.
     */
    private void logCandleSummary(String label, List<Candle> candles) {
        if (candles.isEmpty()) {
            log.warn("{} candles: count=0", label);
            return;
        }

        if (!marketProperties.seedLogDetailEnabled()) {
            log.info("{} candles: count={}", label, candles.size());
            return;
        }

        Candle first = candles.get(0);
        Candle last = candles.get(candles.size() - 1);
        log.info(
                "{} candles: count={}, first=[time={} O={} H={} L={} C={} V={}], last=[time={} O={} H={} L={} C={} V={}]",
                label,
                candles.size(),
                first.time(), first.open(), first.high(), first.low(), first.close(), first.volume(),
                last.time(), last.open(), last.high(), last.low(), last.close(), last.volume());
    }

    /** Result of {@link #bootstrapSymbol(WatchlistEntry)}. */
    public record BootstrapSymbolResult(
            String symbolId,
            SymbolBootstrapStatus status,
            int seededTimeframeCount,
            int seededCandleCount,
            String error
    ) {
        static BootstrapSymbolResult ready(String symbolId, int tfCount, int candleCount) {
            return new BootstrapSymbolResult(
                    symbolId, SymbolBootstrapStatus.READY, tfCount, candleCount, null);
        }

        static BootstrapSymbolResult failed(String symbolId, String error) {
            return new BootstrapSymbolResult(
                    symbolId, SymbolBootstrapStatus.FAILED, 0, 0, error);
        }

        static BootstrapSymbolResult cancelled(String symbolId) {
            return new BootstrapSymbolResult(
                    symbolId, SymbolBootstrapStatus.REMOVING, 0, 0, "cancelled");
        }
    }
}
