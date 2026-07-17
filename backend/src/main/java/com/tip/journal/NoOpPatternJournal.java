package com.tip.journal;

import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Active when watchlist store is memory (no DB).
 */
@Component
@ConditionalOnProperty(name = "tip.watchlist.store", havingValue = "memory", matchIfMissing = true)
public class NoOpPatternJournal implements PatternJournal {

    private final PatternJournal.NoOp delegate = PatternJournal.NoOp.INSTANCE;

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public void insertDetected(ActivePattern pattern, PatternStageEvent detectedEvent) {
        delegate.insertDetected(pattern, detectedEvent);
    }

    @Override
    public void applyEvents(ActivePattern pattern, List<PatternStageEvent> events) {
        delegate.applyEvents(pattern, events);
    }

    @Override
    public void persistLifecycle(ActivePattern pattern, List<PatternStageEvent> allEvents) {
        delegate.persistLifecycle(pattern, allEvents);
    }

    @Override
    public List<ActivePattern> findOpen(String symbolId, String timeframe) {
        return delegate.findOpen(symbolId, timeframe);
    }

    @Override
    public List<ActivePattern> findOpenForSymbol(String symbolId) {
        return delegate.findOpenForSymbol(symbolId);
    }

    @Override
    public List<ActivePattern> findOpenByTimeframes(Set<String> timeframes) {
        return delegate.findOpenByTimeframes(timeframes);
    }

    @Override
    public List<ActivePattern> findRecent(String symbolId, String statusFilter, int limit) {
        return delegate.findRecent(symbolId, statusFilter, limit);
    }

    @Override
    public Optional<PatternStatisticsSnapshot> findStatistics(
            String symbolId, String patternType, String timeframe
    ) {
        return delegate.findStatistics(symbolId, patternType, timeframe);
    }

    @Override
    public List<PatternStatisticsSnapshot> findStatisticsForSymbols(
            Collection<String> symbolIds,
            String patternType,
            String timeframe
    ) {
        return delegate.findStatisticsForSymbols(symbolIds, patternType, timeframe);
    }
}
