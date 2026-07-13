package com.tip.patterns;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import com.tip.market.CandleEngine;
import com.tip.market.event.CandleClosedEvent;
import com.tip.market.model.Candle;
import com.tip.patterns.breakout.BreakoutBarEvaluation;
import com.tip.patterns.breakout.BreakoutBarEvaluator;
import com.tip.patterns.breakout.BreakoutConfig;
import com.tip.patterns.breakout.BreakoutLifecycle;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.watchlist.WatchlistEntry;
import com.tip.watchlist.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for closed candles and runs Breakout detect/lifecycle + journal.
 */
@Component
public class PatternEvaluator {

    private static final Logger log = LoggerFactory.getLogger(PatternEvaluator.class);

    private final PatternFeatureGuard featureGuard;
    private final PatternProperties patternProperties;
    private final CandleEngine candleEngine;
    private final WatchlistRepository watchlistRepository;
    private final ActiveInstanceStore activeInstanceStore;
    private final PatternJournal patternJournal;
    private final PatternEventPublisher patternEventPublisher;
    private final Map<String, Object> seriesLocks = new ConcurrentHashMap<>();

    public PatternEvaluator(
            PatternFeatureGuard featureGuard,
            PatternProperties patternProperties,
            CandleEngine candleEngine,
            WatchlistRepository watchlistRepository,
            ActiveInstanceStore activeInstanceStore,
            PatternJournal patternJournal,
            PatternEventPublisher patternEventPublisher
    ) {
        this.featureGuard = featureGuard;
        this.patternProperties = patternProperties;
        this.candleEngine = candleEngine;
        this.watchlistRepository = watchlistRepository;
        this.activeInstanceStore = activeInstanceStore;
        this.patternJournal = patternJournal;
        this.patternEventPublisher = patternEventPublisher;
    }

    @EventListener
    public void onCandleClosed(CandleClosedEvent event) {
        if (!featureGuard.isFullyEnabled()) {
            return;
        }
        String symbolId = event.instrumentKey();
        String timeframe = event.timeframe();
        Object lock = seriesLocks.computeIfAbsent(symbolId + "|" + timeframe, k -> new Object());
        synchronized (lock) {
            evaluateUnlocked(symbolId, timeframe, event.candle());
        }
    }

    private void evaluateUnlocked(String symbolId, String timeframe, Candle signal) {
        try {
            List<Candle> closed = candleEngine.getClosedCandles(symbolId, timeframe);
            if (closed.isEmpty()) {
                return;
            }

            boolean index = watchlistRepository.findBySymbolId(symbolId)
                    .map(WatchlistEntry::segment)
                    .map(s -> s != null && s.toUpperCase().contains("INDEX"))
                    .orElse(symbolId != null && symbolId.startsWith("NSE_INDEX"));

            BreakoutConfig config = patternProperties.toBreakoutConfig();
            Instant now = Instant.now();

            List<ActivePattern> open = new ArrayList<>(activeInstanceStore.getOpen(symbolId, timeframe));
            BreakoutBarEvaluation eval = BreakoutBarEvaluator.evaluate(
                    symbolId, timeframe, open, closed, index, config, now
            );

            // Persist + publish (single journal TX per instance via persistLifecycle)
            for (ActivePattern advanced : eval.advanced()) {
                List<PatternStageEvent> stageEvents = eval.events().stream()
                        .filter(e -> e.instanceId().equals(advanced.id()))
                        .toList();
                if (!stageEvents.isEmpty() || advanced.isTerminal()) {
                    patternJournal.persistLifecycle(advanced, stageEvents);
                    patternEventPublisher.publish(advanced, stageEvents);
                }
            }

            for (ActivePattern detected : eval.newlyDetected()) {
                List<PatternStageEvent> all = eval.events().stream()
                        .filter(e -> e.instanceId().equals(detected.id()))
                        .toList();
                patternJournal.persistLifecycle(detected, all);
                patternEventPublisher.publish(detected, all);
            }

            // Policy expiry for multi-day max candles / sessions on this bar
            List<ActivePattern> still = new ArrayList<>(eval.stillOpen());
            still = applyDurationPolicies(still, signal, timeframe, config, now);

            activeInstanceStore.replaceOpen(symbolId, timeframe, still);
        } catch (RuntimeException ex) {
            log.warn("Pattern evaluation failed for {} {}: {}", symbolId, timeframe, ex.toString());
        }
    }

    private List<ActivePattern> applyDurationPolicies(
            List<ActivePattern> open,
            Candle signal,
            String timeframe,
            BreakoutConfig config,
            Instant now
    ) {
        List<ActivePattern> kept = new ArrayList<>();
        for (ActivePattern p : open) {
            boolean expire = false;
            String reason = null;
            if ("4h".equals(timeframe)) {
                if (p.durationCandles() >= patternProperties.getExpiry().getMaxCandles4h()) {
                    expire = true;
                    reason = "max_candles";
                } else if (p.sessionsSeen() >= patternProperties.getExpiry().getMaxSessions4h()) {
                    expire = true;
                    reason = "max_sessions";
                }
            } else if ("1d".equals(timeframe)
                    && p.durationCandles() >= patternProperties.getExpiry().getMaxCandles1d()) {
                expire = true;
                reason = "max_candles";
            }
            if (expire) {
                List<PatternStageEvent> ev = BreakoutLifecycle.expire(p, signal, reason, now, false);
                patternJournal.applyEvents(p, ev);
                patternEventPublisher.publish(p, ev);
            } else {
                kept.add(p);
            }
        }
        return kept;
    }
}
