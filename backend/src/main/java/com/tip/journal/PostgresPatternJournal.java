package com.tip.journal;

import com.tip.journal.entity.PatternEventEntity;
import com.tip.journal.entity.PatternInstanceEntity;
import com.tip.journal.entity.PatternOutcomeEntity;
import com.tip.journal.entity.PatternStatisticsEntity;
import com.tip.journal.repo.PatternEventJpaRepository;
import com.tip.journal.repo.PatternInstanceJpaRepository;
import com.tip.journal.repo.PatternOutcomeJpaRepository;
import com.tip.journal.repo.PatternStatisticsJpaRepository;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.FinalOutcome;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternStageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Repository
@ConditionalOnProperty(name = "tip.watchlist.store", havingValue = "postgres")
public class PostgresPatternJournal implements PatternJournal {

    private static final Logger log = LoggerFactory.getLogger(PostgresPatternJournal.class);

    private final PatternInstanceJpaRepository instances;
    private final PatternEventJpaRepository events;
    private final PatternOutcomeJpaRepository outcomes;
    private final PatternStatisticsJpaRepository statistics;

    public PostgresPatternJournal(
            PatternInstanceJpaRepository instances,
            PatternEventJpaRepository events,
            PatternOutcomeJpaRepository outcomes,
            PatternStatisticsJpaRepository statistics
    ) {
        this.instances = instances;
        this.events = events;
        this.outcomes = outcomes;
        this.statistics = statistics;
    }

    @Override
    public boolean isActive() {
        return true;
    }

    @Override
    @Transactional
    public void insertDetected(ActivePattern pattern, PatternStageEvent detectedEvent) {
        Instant now = Instant.now();
        if (instances.existsById(pattern.id())) {
            log.debug("Pattern instance already exists: {}", pattern.id());
            return;
        }
        instances.save(PatternInstanceMapper.toNewEntity(pattern, now));
        if (detectedEvent != null) {
            insertEventIfAbsent(detectedEvent);
        }
    }

    @Override
    @Transactional
    public void applyEvents(ActivePattern pattern, List<PatternStageEvent> stageEvents) {
        persistMutableAndEvents(pattern, stageEvents, Instant.now());
    }

    /**
     * Single transaction for detect + same-bar (and any lifecycle batch).
     */
    @Override
    @Transactional
    public void persistLifecycle(ActivePattern pattern, List<PatternStageEvent> allEvents) {
        Instant now = Instant.now();
        if (allEvents == null) {
            allEvents = List.of();
        }
        boolean needsInsert = !instances.existsById(pattern.id());
        if (needsInsert) {
            instances.save(PatternInstanceMapper.toNewEntity(pattern, now));
        }
        persistMutableAndEvents(pattern, allEvents, now);
    }

    private void persistMutableAndEvents(
            ActivePattern pattern,
            List<PatternStageEvent> stageEvents,
            Instant now
    ) {
        if (stageEvents == null) {
            stageEvents = List.of();
        }
        if (stageEvents.isEmpty() && !pattern.isTerminal()) {
            instances.findById(pattern.id()).ifPresent(e -> {
                PatternInstanceMapper.copyMutable(pattern, e, now);
                instances.save(e);
            });
            return;
        }

        PatternInstanceEntity entity = instances.findById(pattern.id()).orElse(null);
        if (entity == null) {
            instances.save(PatternInstanceMapper.toNewEntity(pattern, now));
            entity = instances.findById(pattern.id()).orElseThrow();
        }

        for (PatternStageEvent ev : stageEvents) {
            insertEventIfAbsent(ev);
        }

        PatternInstanceMapper.copyMutable(pattern, entity, now);
        instances.save(entity);

        if (pattern.isTerminal() && pattern.finalOutcome() != null) {
            if (!outcomes.existsById(pattern.id())) {
                outcomes.save(buildOutcome(pattern, now));
                upsertStatistics(pattern, now);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivePattern> findOpen(String symbolId, String timeframe) {
        return instances.findBySymbolIdAndTimeframeAndStatusIn(
                        symbolId, timeframe, PatternInstanceMapper.OPEN_STATUSES)
                .stream()
                .map(PatternInstanceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivePattern> findOpenForSymbol(String symbolId) {
        return instances.findBySymbolIdAndStatusIn(symbolId, PatternInstanceMapper.OPEN_STATUSES)
                .stream()
                .map(PatternInstanceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivePattern> findOpenByTimeframes(Set<String> timeframes) {
        if (timeframes == null || timeframes.isEmpty()) {
            return List.of();
        }
        return instances.findByTimeframeInAndStatusIn(timeframes, PatternInstanceMapper.OPEN_STATUSES)
                .stream()
                .map(PatternInstanceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActivePattern> findRecent(String symbolId, String statusFilter, int limit) {
        String filter = statusFilter == null ? "all" : statusFilter.trim().toLowerCase(Locale.ROOT);
        Collection<String> statuses = switch (filter) {
            case "active" -> PatternInstanceMapper.OPEN_STATUSES;
            case "closed" -> PatternInstanceMapper.CLOSED_STATUSES;
            default -> null; // all
        };
        List<PatternInstanceEntity> rows = statuses == null
                ? instances.findBySymbolIdOrderByDetectedAtDesc(symbolId)
                : instances.findBySymbolIdAndStatusInOrderByDetectedAtDesc(symbolId, statuses);
        return rows.stream()
                .limit(Math.max(1, limit))
                .map(PatternInstanceMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PatternStatisticsSnapshot> findStatistics(
            String symbolId, String patternType, String timeframe
    ) {
        return statistics.findBySymbolIdAndPatternTypeAndTimeframe(symbolId, patternType, timeframe)
                .map(s -> new PatternStatisticsSnapshot(
                        s.getSymbolId(),
                        s.getPatternType(),
                        s.getTimeframe(),
                        s.getSampleSize(),
                        s.getSuccessCount(),
                        s.getFailCount(),
                        s.getExpiredCount(),
                        s.getSuccessRate(),
                        s.getResolvedSuccessRate(),
                        s.getSuccessCount() + s.getFailCount(),
                        s.getAvgMoveR(),
                        s.getAvgDurationCandles(),
                        s.getAvgMfeR(),
                        s.getAvgMaeR(),
                        s.getMoveSampleSize(),
                        s.getMfeSampleSize(),
                        s.getMaeSampleSize(),
                        s.getUpdatedAt()
                ));
    }

    private void insertEventIfAbsent(PatternStageEvent ev) {
        String type = ev.stage().wireValue();
        if (events.existsByPatternInstanceIdAndEventType(ev.instanceId(), type)) {
            return;
        }
        PatternEventEntity e = new PatternEventEntity();
        e.setPatternInstanceId(ev.instanceId());
        e.setEventType(type);
        e.setEventTime(ev.eventTime() != null ? ev.eventTime() : Instant.now());
        e.setCandleTime(ev.candleTime());
        e.setPriceAtEvent(ev.priceAtEvent());
        e.setCreatedAt(Instant.now());
        events.save(e);
    }

    private PatternOutcomeEntity buildOutcome(ActivePattern p, Instant now) {
        PatternOutcomeEntity o = new PatternOutcomeEntity();
        o.setPatternInstanceId(p.id());
        o.setFinalOutcome(p.finalOutcome().wireValue());
        o.setDurationCandles(p.durationCandles());
        long seconds = 0;
        if (p.detectedAt() != null) {
            seconds = Math.max(0, Duration.between(p.detectedAt(), now).getSeconds());
        }
        o.setDurationSeconds(seconds);
        String reason = p.terminalReason();
        if (p.suppressExcursionStats() || "startup_recovery".equals(reason)) {
            o.setMaxFavorableR(null);
            o.setMaxAdverseR(null);
            o.setMaxFavorablePrice(null);
            o.setMaxAdversePrice(null);
            o.setMoveR(null);
            o.setEndPrice(null);
        } else {
            o.setMaxFavorableR(p.maxFavorableR());
            o.setMaxAdverseR(p.maxAdverseR());
            o.setMaxFavorablePrice(p.mfePrice());
            o.setMaxAdversePrice(p.maePrice());
            o.setMoveR(p.moveR());
            o.setEndPrice(p.endPrice());
        }
        o.setReason(reason);
        o.setClosedAt(now);
        return o;
    }

    private void upsertStatistics(ActivePattern p, Instant now) {
        String type = p.patternType().wireValue();
        PatternStatisticsEntity s = statistics
                .findForUpdate(p.symbolId(), type, p.timeframe())
                .orElseGet(() -> {
                    PatternStatisticsEntity n = new PatternStatisticsEntity();
                    n.setSymbolId(p.symbolId());
                    n.setPatternType(type);
                    n.setTimeframe(p.timeframe());
                    return n;
                });

        s.setSampleSize(s.getSampleSize() + 1);
        FinalOutcome fo = p.finalOutcome();
        if (fo == FinalOutcome.SUCCEEDED) {
            s.setSuccessCount(s.getSuccessCount() + 1);
        } else if (fo == FinalOutcome.FAILED) {
            s.setFailCount(s.getFailCount() + 1);
        } else {
            s.setExpiredCount(s.getExpiredCount() + 1);
        }

        int sample = s.getSampleSize();
        s.setSuccessRate(sample == 0 ? 0 : (double) s.getSuccessCount() / sample);
        int resolved = s.getSuccessCount() + s.getFailCount();
        s.setResolvedSuccessRate(resolved == 0 ? null : (double) s.getSuccessCount() / resolved);

        s.setAvgDurationCandles(runningAvg(
                s.getAvgDurationCandles(), sample - 1, p.durationCandles(), sample));

        boolean skipExcursions = p.suppressExcursionStats()
                || "startup_recovery".equals(p.terminalReason());
        if (!skipExcursions) {
            Double move = p.moveR();
            if (move != null) {
                int n = s.getMoveSampleSize();
                s.setAvgMoveR(runningAvg(s.getAvgMoveR(), n, move, n + 1));
                s.setMoveSampleSize(n + 1);
            }
            double mfe = p.maxFavorableR();
            int nm = s.getMfeSampleSize();
            s.setAvgMfeR(runningAvg(s.getAvgMfeR(), nm, mfe, nm + 1));
            s.setMfeSampleSize(nm + 1);

            double mae = p.maxAdverseR();
            int na = s.getMaeSampleSize();
            s.setAvgMaeR(runningAvg(s.getAvgMaeR(), na, mae, na + 1));
            s.setMaeSampleSize(na + 1);
        }

        s.setUpdatedAt(now);
        statistics.save(s);
    }

    private static double runningAvg(double prevAvg, int prevN, double value, int newN) {
        if (newN <= 0) {
            return 0;
        }
        if (prevN <= 0) {
            return value;
        }
        return (prevAvg * prevN + value) / newN;
    }
}
