package com.tip.patterns;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import com.tip.market.CandleEngine;
import com.tip.market.MarketPhase;
import com.tip.market.event.MarketPhaseChangedEvent;
import com.tip.market.model.Candle;
import com.tip.patterns.breakout.BreakoutLifecycle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Session-close expiry for intraday TFs; increments 4h session counters.
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

    public SessionExpiryReconciler(
            PatternFeatureGuard featureGuard,
            PatternProperties patternProperties,
            ActiveInstanceStore activeInstanceStore,
            PatternJournal patternJournal,
            PatternEventPublisher patternEventPublisher,
            CandleEngine candleEngine
    ) {
        this.featureGuard = featureGuard;
        this.patternProperties = patternProperties;
        this.activeInstanceStore = activeInstanceStore;
        this.patternJournal = patternJournal;
        this.patternEventPublisher = patternEventPublisher;
        this.candleEngine = candleEngine;
    }

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
        int expired = 0;
        for (ActivePattern p : all) {
            if (patternProperties.isSessionCloseTimeframe(p.timeframe())) {
                Candle last = lastClosed(p);
                List<PatternStageEvent> ev = BreakoutLifecycle.expire(p, last, "session_end", now, false);
                patternJournal.applyEvents(p, ev);
                patternEventPublisher.publish(p, ev);
                activeInstanceStore.remove(p);
                expired++;
            } else if ("4h".equals(p.timeframe())) {
                p.setSessionsSeen(p.sessionsSeen() + 1);
                patternJournal.applyEvents(p, List.of());
                if (p.sessionsSeen() >= patternProperties.getExpiry().getMaxSessions4h()) {
                    Candle last = lastClosed(p);
                    List<PatternStageEvent> ev = BreakoutLifecycle.expire(p, last, "max_sessions", now, false);
                    patternJournal.applyEvents(p, ev);
                    patternEventPublisher.publish(p, ev);
                    activeInstanceStore.remove(p);
                    expired++;
                } else {
                    activeInstanceStore.put(p);
                }
            }
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
