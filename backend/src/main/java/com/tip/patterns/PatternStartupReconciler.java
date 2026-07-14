package com.tip.patterns;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import com.tip.market.CandleEngine;
import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.watchlist.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * PI-15: expire session_close opens on startup; hydrate multi-day opens.
 */
@Component
public class PatternStartupReconciler {

    private static final Logger log = LoggerFactory.getLogger(PatternStartupReconciler.class);

    private final PatternFeatureGuard featureGuard;
    private final PatternProperties patternProperties;
    private final PatternJournal patternJournal;
    private final ActiveInstanceStore activeInstanceStore;
    private final CandleEngine candleEngine;
    private final PatternEventPublisher patternEventPublisher;
    private final WatchlistRepository watchlistRepository;

    public PatternStartupReconciler(
            PatternFeatureGuard featureGuard,
            PatternProperties patternProperties,
            PatternJournal patternJournal,
            ActiveInstanceStore activeInstanceStore,
            CandleEngine candleEngine,
            PatternEventPublisher patternEventPublisher,
            WatchlistRepository watchlistRepository
    ) {
        this.featureGuard = featureGuard;
        this.patternProperties = patternProperties;
        this.patternJournal = patternJournal;
        this.activeInstanceStore = activeInstanceStore;
        this.candleEngine = candleEngine;
        this.patternEventPublisher = patternEventPublisher;
        this.watchlistRepository = watchlistRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(100)
    public void onReady() {
        if (!featureGuard.isFullyEnabled()) {
            log.info("Pattern startup reconcile skipped (patterns not fully enabled)");
            return;
        }
        Instant now = Instant.now();
        Set<String> sessionTfs = patternProperties.getExpiry().sessionCloseTimeframes();
        List<ActivePattern> sessionOpens = patternJournal.findOpenByTimeframes(sessionTfs);
        for (ActivePattern p : sessionOpens) {
            List<PatternStageEvent> ev = PatternLifecycleSupport.expire(
                    p, null, "startup_recovery", now, true);
            patternJournal.applyEvents(p, ev);
            patternEventPublisher.publish(p, ev);
        }
        log.info("Pattern startup: expired {} session-close open instance(s)", sessionOpens.size());

        // 1h / 4h / 1d: hydrate and resume (not mass-expired on restart)
        Set<String> multiDay = Set.of("1h", "4h", "1d");
        List<ActivePattern> multi = patternJournal.findOpenByTimeframes(multiDay);
        int hydrated = 0;
        int dropped = 0;
        for (ActivePattern p : multi) {
            boolean onWatchlist = watchlistRepository.containsSymbolId(p.symbolId())
                    || watchlistRepository.findBySymbolId(p.symbolId()).isPresent();
            List<Candle> closed = candleEngine.getClosedCandles(p.symbolId(), p.timeframe());
            if (!onWatchlist || closed.isEmpty()) {
                String reason = onWatchlist ? "startup_recovery" : "symbol_removed";
                List<PatternStageEvent> ev = PatternLifecycleSupport.expire(p, null, reason, now, true);
                patternJournal.applyEvents(p, ev);
                patternEventPublisher.publish(p, ev);
                dropped++;
                continue;
            }
            recomputeExcursions(p, closed);
            activeInstanceStore.put(p);
            hydrated++;
        }
        log.info("Pattern startup: hydrated {} multi-day open instance(s), expired {} without series",
                hydrated, dropped);
    }

    private void recomputeExcursions(ActivePattern p, List<Candle> closed) {
        double mfe = p.entryPrice();
        double mae = p.entryPrice();
        int duration = 0;
        for (Candle c : closed) {
            if (c.time() < p.detectCandleTime()) {
                continue;
            }
            duration++;
            mfe = Math.max(mfe, c.high());
            mae = Math.min(mae, c.low());
        }
        if (duration > 0) {
            p.setMfePrice(mfe);
            p.setMaePrice(mae);
            p.setDurationCandles(duration);
        }
    }
}
