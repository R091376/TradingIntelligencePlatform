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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Expire open patterns when a watchlist symbol is removed (PI-27).
 * Prefer in-memory instances (real MFE/MAE); journal-only reloads suppress excursion stats (PI-23).
 */
@Component
public class WatchlistRemovePatternListener {

    private static final Logger log = LoggerFactory.getLogger(WatchlistRemovePatternListener.class);

    private final PatternFeatureGuard featureGuard;
    private final ActiveInstanceStore activeInstanceStore;
    private final PatternJournal patternJournal;
    private final PatternEventPublisher patternEventPublisher;

    public WatchlistRemovePatternListener(
            PatternFeatureGuard featureGuard,
            ActiveInstanceStore activeInstanceStore,
            PatternJournal patternJournal,
            PatternEventPublisher patternEventPublisher
    ) {
        this.featureGuard = featureGuard;
        this.activeInstanceStore = activeInstanceStore;
        this.patternJournal = patternJournal;
        this.patternEventPublisher = patternEventPublisher;
    }

    @EventListener
    public void onRemoved(WatchlistSymbolRemovedEvent event) {
        if (!featureGuard.isFullyEnabled()) {
            activeInstanceStore.removeAllForSymbol(event.symbolId());
            return;
        }
        String symbolId = event.symbolId();
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

        int expired = 0;
        for (ActivePattern p : byId.values()) {
            boolean memory = Boolean.TRUE.equals(fromMemory.get(p.id()));
            // Memory path keeps MFE/MAE; journal-only has entry-seeded mfe/mae → NULL excursions
            List<PatternStageEvent> ev = PatternLifecycleSupport.expire(
                    p, null, "symbol_removed", now, !memory);
            patternJournal.applyEvents(p, ev);
            patternEventPublisher.publish(p, ev);
            expired++;
        }
        activeInstanceStore.removeAllForSymbol(symbolId);
        log.info("Expired {} open patterns for removed symbol {}", expired, symbolId);
    }
}
