package com.tip.patterns;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import com.tip.market.CandleEngine;
import com.tip.market.MarketPhase;
import com.tip.market.event.MarketPhaseChangedEvent;
import com.tip.market.model.Candle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session-close expiry for short intraday TFs; session counters for 1h/4h multi-day setups.
 * Uses {@link PatternSeriesGate} so expiry never races candle evaluation on the same series.
 * Runs after {@link com.tip.market.CandleCloseReconciler} so the last session bar is closed first.
 */
@Component
public class SessionExpiryReconciler {

    private static final Logger log = LoggerFactory.getLogger(SessionExpiryReconciler.class);

    private final PatternFeatureGuard featureGuard;
    private final PatternProperties patternProperties;
    private final ActiveInstanceStore activeInstanceStore;
    private final PatternJournal patternJournal;
    private final PatternEventPublisher patternEventPublisher;
    private final CandleEngine candleEngine;
    private final PatternSeriesGate seriesGate;

    public SessionExpiryReconciler(
            PatternFeatureGuard featureGuard,
            PatternProperties patternProperties,
            ActiveInstanceStore activeInstanceStore,
            PatternJournal patternJournal,
            PatternEventPublisher patternEventPublisher,
            CandleEngine candleEngine,
            PatternSeriesGate seriesGate
    ) {
        this.featureGuard = featureGuard;
        this.patternProperties = patternProperties;
        this.activeInstanceStore = activeInstanceStore;
        this.patternJournal = patternJournal;
        this.patternEventPublisher = patternEventPublisher;
        this.candleEngine = candleEngine;
        this.seriesGate = seriesGate;
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @EventListener
    public void onPhaseChanged(MarketPhaseChangedEvent event) {
        if (!featureGuard.isFullyEnabled()) {
            return;
        }
        if (event.phase() != MarketPhase.CLOSED) {
            return;
        }
        Instant now = Instant.now();
        List<ActivePattern> all = activeInstanceStore.snapshotAllOpen();

        // One series at a time (no nested multi-key locks)
        Map<String, List<ActivePattern>> bySeries = new LinkedHashMap<>();
        for (ActivePattern p : all) {
            String k = PatternSeriesGate.key(p.symbolId(), p.timeframe());
            bySeries.computeIfAbsent(k, ignored -> new ArrayList<>()).add(p);
        }

        int expired = 0;
        for (Map.Entry<String, List<ActivePattern>> e : bySeries.entrySet()) {
            ActivePattern sample = e.getValue().get(0);
            int[] n = {0};
            seriesGate.run(sample.symbolId(), sample.timeframe(), () -> {
                for (ActivePattern p : e.getValue()) {
                    if (p.isTerminal()) {
                        continue;
                    }
                    if (patternProperties.isSessionCloseTimeframe(p.timeframe())) {
                        Candle last = lastClosed(p);
                        List<PatternStageEvent> ev =
                                PatternLifecycleSupport.expire(p, last, "session_end", now, false);
                        patternJournal.applyEvents(p, ev);
                        patternEventPublisher.publish(p, ev);
                        activeInstanceStore.remove(p);
                        n[0]++;
                    } else if (patternProperties.tracksSessionsOnClose(p.timeframe())) {
                        p.setSessionsSeen(p.sessionsSeen() + 1);
                        patternJournal.applyEvents(p, List.of());
                        if (p.sessionsSeen() >= patternProperties.maxSessionsFor(p.timeframe())) {
                            Candle last = lastClosed(p);
                            List<PatternStageEvent> ev =
                                    PatternLifecycleSupport.expire(p, last, "max_sessions", now, false);
                            patternJournal.applyEvents(p, ev);
                            patternEventPublisher.publish(p, ev);
                            activeInstanceStore.remove(p);
                            n[0]++;
                        } else {
                            activeInstanceStore.put(p);
                        }
                    }
                }
            });
            expired += n[0];
        }
        log.info("Session close: expired {} open pattern instance(s)", expired);
    }

    private Candle lastClosed(ActivePattern p) {
        List<Candle> closed = candleEngine.getClosedCandles(p.symbolId(), p.timeframe());
        if (closed.isEmpty()) {
            return null;
        }
        return closed.get(closed.size() - 1);
    }
}
