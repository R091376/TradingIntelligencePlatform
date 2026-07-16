package com.tip.market;

import com.tip.market.event.MarketPhaseChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Force-closes open candles when their bucket has ended without a subsequent tick
 * (session end, quiet symbols). Runs before session-expiry pattern handling.
 */
@Component
public class CandleCloseReconciler {

    private static final Logger log = LoggerFactory.getLogger(CandleCloseReconciler.class);

    private final CandleEngine candleEngine;
    private final MarketStatusService marketStatusService;

    public CandleCloseReconciler(CandleEngine candleEngine, MarketStatusService marketStatusService) {
        this.candleEngine = candleEngine;
        this.marketStatusService = marketStatusService;
    }

    /**
     * On session close, finalize any bars still open from the last session bucket.
     * Highest precedence so {@link com.tip.patterns.SessionExpiryReconciler} sees closed bars.
     */
    @Order(Ordered.HIGHEST_PRECEDENCE)
    @EventListener
    public void onPhaseChanged(MarketPhaseChangedEvent event) {
        if (event.phase() != MarketPhase.CLOSED) {
            return;
        }
        int n = candleEngine.closeStaleOpenCandles(System.currentTimeMillis());
        if (n > 0) {
            log.info("Session close: force-closed {} open candle series", n);
        }
    }

    /**
     * Periodic pass during open hours so quiet symbols still roll bars on the clock.
     */
    @Scheduled(fixedRateString = "${tip.market.candle-close-refresh-ms:15000}")
    public void closeStaleFromClock() {
        try {
            MarketPhase phase = marketStatusService.getMarketPhase();
            if (phase == MarketPhase.CLOSED) {
                // Still finalize if phase flipped while a bar was open.
                int n = candleEngine.closeStaleOpenCandles(System.currentTimeMillis());
                if (n > 0) {
                    log.info("Clock reconciler: force-closed {} open candle series (market closed)", n);
                }
                return;
            }
            int n = candleEngine.closeStaleOpenCandles(System.currentTimeMillis());
            if (n > 0) {
                log.debug("Clock reconciler: force-closed {} stale open candle series", n);
            }
        } catch (RuntimeException ex) {
            log.warn("Candle close reconciler failed: {}", ex.toString());
        }
    }
}
