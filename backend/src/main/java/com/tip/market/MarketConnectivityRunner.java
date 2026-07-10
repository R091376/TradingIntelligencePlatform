package com.tip.market;

import com.tip.instrument.InstrumentMasterCache;
import com.tip.watchlist.WatchlistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Synchronous startup: load instrument master → seed watchlist → bootstrap all active symbols.
 * <p>
 * Blocks intentionally (KD20). Spring may accept HTTP before this runner finishes; the
 * process bootstrap status is forced to {@link BootstrapStatus#PENDING} at the start of
 * {@link #run} and remains non-READY until {@code recoverAllActive} completes (READY/FAILED).
 * Clients should treat {@code PENDING} as “still loading” and poll {@code /api/market/status}
 * until READY or FAILED before relying on candles/WS for the primary chart symbol.
 */
@Component
public class MarketConnectivityRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MarketConnectivityRunner.class);

    private final MarketStatusService marketStatusService;
    private final InstrumentMasterCache instrumentMasterCache;
    private final WatchlistService watchlistService;
    private final MarketBootstrapService marketBootstrapService;

    public MarketConnectivityRunner(
            MarketStatusService marketStatusService,
            InstrumentMasterCache instrumentMasterCache,
            WatchlistService watchlistService,
            MarketBootstrapService marketBootstrapService
    ) {
        this.marketStatusService = marketStatusService;
        this.instrumentMasterCache = instrumentMasterCache;
        this.watchlistService = watchlistService;
        this.marketBootstrapService = marketBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Ensure HTTP clients never observe a stale READY mid multi-symbol recovery.
        marketStatusService.setBootstrapPending();
        log.info("Market connectivity: bootstrap PENDING (master → seed → recoverAllActive)");

        instrumentMasterCache.ensureLoaded();
        watchlistService.ensureSeeded();
        marketBootstrapService.recoverAllActive();
    }
}
