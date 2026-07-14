package com.tip.patterns;

import com.tip.journal.PatternJournal;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.watchlist.event.WatchlistSymbolRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expire open patterns when a watchlist symbol is removed (PI-27).
 * Prefer in-memory instances (real MFE/MAE); journal-only reloads suppress excursion stats (PI-23).
 * Uses {@link PatternSeriesGate} so remove never races candle evaluation on the same series.
 */
@Component
public class WatchlistRemovePatternListener {

    private static final Logger log = LoggerFactory.getLogger(WatchlistRemovePatternListener.class);

    private final PatternFeatureGuard featureGuard;
    private final ActiveInstanceStore activeInstanceStore;
    private final PatternJournal patternJournal;
    private final PatternEventPublisher patternEventPublisher;
    private final PatternSeriesGate seriesGate;

    public WatchlistRemovePatternListener(
            PatternFeatureGuard featureGuard,
            ActiveInstanceStore activeInstanceStore,
            PatternJournal patternJournal,
            PatternEventPublisher patternEventPublisher,
            PatternSeriesGate seriesGate
    ) {
        this.featureGuard = featureGuard;
        this.activeInstanceStore = activeInstanceStore;
        this.patternJournal = patternJournal;
        this.patternEventPublisher = patternEventPublisher;
        this.seriesGate = seriesGate;
    }

    @EventListener
    public void onRemoved(WatchlistSymbolRemovedEvent event) {
        String symbolId = event.symbolId();
        if (!featureGuard.isFullyEnabled()) {
            activeInstanceStore.removeAllForSymbol(symbolId);
            seriesGate.clearSymbol(symbolId);
            return;
        }
        Instant now = Instant.now();

        Map<UUID, ActivePattern> byId = new LinkedHashMap<>();
        Map<UUID, Boolean> fromMemory = new LinkedHashMap<>();

        for (ActivePattern p : activeInstanceStore.getOpenForSymbol(symbolId)) {
            byId.put(p.id(), p);
            fromMemory.put(p.id(), true);
        }
        for (ActivePattern p : patternJournal.findOpenForSymbol(symbolId)) {
            if (!byId.containsKey(p.id())) {
                byId.put(p.id(), p);
                fromMemory.put(p.id(), false);
            }
        }

        // Group by series so each lock is held alone (no nested multi-key locks)
        Map<String, List<ActivePattern>> bySeries = new LinkedHashMap<>();
        for (ActivePattern p : byId.values()) {
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
                    boolean memory = Boolean.TRUE.equals(fromMemory.get(p.id()));
                    List<PatternStageEvent> ev = PatternLifecycleSupport.expire(
                            p, null, "symbol_removed", now, !memory);
                    patternJournal.applyEvents(p, ev);
                    patternEventPublisher.publish(p, ev);
                    n[0]++;
                }
            });
            expired += n[0];
        }
        activeInstanceStore.removeAllForSymbol(symbolId);
        seriesGate.clearSymbol(symbolId);
        log.info("Expired {} open patterns for removed symbol {}", expired, symbolId);
    }
}
