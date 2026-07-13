package com.tip.market;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reconciles {@link MarketPhase} from the wall clock so session close
 * is observed even if the Upstox feed is down or stuck OPEN.
 *
 * <p>Does not close candles — only market phase. Used later by pattern session expiry.
 */
@Component
public class MarketPhaseClockReconciler {

    private static final Logger log = LoggerFactory.getLogger(MarketPhaseClockReconciler.class);

    private final MarketStatusService marketStatusService;

    public MarketPhaseClockReconciler(MarketStatusService marketStatusService) {
        this.marketStatusService = marketStatusService;
    }

    @Scheduled(fixedRateString = "${tip.market.phase-refresh-ms:30000}")
    public void refreshPhaseFromClock() {
        try {
            marketStatusService.refreshPhaseFromClock();
        } catch (RuntimeException ex) {
            log.warn("Market phase clock refresh failed: {}", ex.toString());
        }
    }
}
