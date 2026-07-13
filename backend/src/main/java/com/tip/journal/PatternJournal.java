package com.tip.journal;

import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Durability boundary for pattern intelligence.
 *
 * <p>Docs: {@code docs/modules/journal.md}
 */
public interface PatternJournal {

    boolean isActive();

    /** Persist new Detected instance + detected event. */
    void insertDetected(ActivePattern pattern, PatternStageEvent detectedEvent);

    /**
     * Persist non-detect stage events and instance flag/status updates.
     * On terminal: outcome row + statistics upsert.
     */
    void applyEvents(ActivePattern pattern, List<PatternStageEvent> events);

    /**
     * Single transaction for detect + same-bar stages (and any later batch).
     * Inserts instance if needed, writes all events, terminal outcome/stats.
     */
    default void persistLifecycle(ActivePattern pattern, List<PatternStageEvent> allEvents) {
        if (allEvents == null || allEvents.isEmpty()) {
            applyEvents(pattern, List.of());
            return;
        }
        boolean hasDetected = allEvents.stream()
                .anyMatch(e -> e.stage() == com.tip.patterns.model.PatternStage.DETECTED);
        if (hasDetected) {
            PatternStageEvent det = allEvents.stream()
                    .filter(e -> e.stage() == com.tip.patterns.model.PatternStage.DETECTED)
                    .findFirst()
                    .orElse(null);
            insertDetected(pattern, det);
            List<PatternStageEvent> rest = allEvents.stream()
                    .filter(e -> e.stage() != com.tip.patterns.model.PatternStage.DETECTED)
                    .toList();
            if (!rest.isEmpty() || pattern.isTerminal()) {
                applyEvents(pattern, rest);
            }
        } else {
            applyEvents(pattern, allEvents);
        }
    }

    List<ActivePattern> findOpen(String symbolId, String timeframe);

    List<ActivePattern> findOpenForSymbol(String symbolId);

    /** Open instances whose timeframe is in {@code timeframes}. */
    List<ActivePattern> findOpenByTimeframes(Set<String> timeframes);

    /**
     * @param statusFilter {@code active} | {@code closed} | {@code all}
     */
    List<ActivePattern> findRecent(String symbolId, String statusFilter, int limit);

    Optional<PatternStatisticsSnapshot> findStatistics(String symbolId, String patternType, String timeframe);

    /** No-op default for memory mode. */
    final class NoOp implements PatternJournal {
        public static final NoOp INSTANCE = new NoOp();

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public void insertDetected(ActivePattern pattern, PatternStageEvent detectedEvent) {
        }

        @Override
        public void applyEvents(ActivePattern pattern, List<PatternStageEvent> events) {
        }

        @Override
        public void persistLifecycle(ActivePattern pattern, List<PatternStageEvent> allEvents) {
        }

        @Override
        public List<ActivePattern> findOpen(String symbolId, String timeframe) {
            return List.of();
        }

        @Override
        public List<ActivePattern> findOpenForSymbol(String symbolId) {
            return List.of();
        }

        @Override
        public List<ActivePattern> findOpenByTimeframes(Set<String> timeframes) {
            return List.of();
        }

        @Override
        public List<ActivePattern> findRecent(String symbolId, String statusFilter, int limit) {
            return List.of();
        }

        @Override
        public Optional<PatternStatisticsSnapshot> findStatistics(
                String symbolId, String patternType, String timeframe
        ) {
            return Optional.empty();
        }
    }

    record PatternStatisticsSnapshot(
            String symbolId,
            String patternType,
            String timeframe,
            int sampleSize,
            int successCount,
            int failCount,
            int expiredCount,
            double successRate,
            Double resolvedSuccessRate,
            int resolvedSampleSize,
            double avgMoveR,
            double avgDurationCandles,
            double avgMfeR,
            double avgMaeR,
            int moveSampleSize,
            int mfeSampleSize,
            int maeSampleSize,
            java.time.Instant updatedAt
    ) {
    }
}
