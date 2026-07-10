package com.tip.market;

import com.tip.instrument.InstrumentMasterCache;
import com.tip.watchlist.WatchlistService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Synchronous startup: load instrument master → seed watchlist → bootstrap all active symbols.
 * Blocks intentionally (KD20) so FE can gate on {@code /api/market/status}.
 */
@Component
public class MarketConnectivityRunner implements ApplicationRunner {

    private final InstrumentMasterCache instrumentMasterCache;
    private final WatchlistService watchlistService;
    private final MarketBootstrapService marketBootstrapService;

    public MarketConnectivityRunner(
            InstrumentMasterCache instrumentMasterCache,
            WatchlistService watchlistService,
            MarketBootstrapService marketBootstrapService
    ) {
        this.instrumentMasterCache = instrumentMasterCache;
        this.watchlistService = watchlistService;
        this.marketBootstrapService = marketBootstrapService;
    }

    @Override
    public void run(ApplicationArguments args) {
        instrumentMasterCache.ensureLoaded();
        watchlistService.ensureSeeded();
        marketBootstrapService.recoverAllActive();
    }
}
