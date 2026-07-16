package com.tip.patterns;

import com.tip.config.PatternProperties;
import com.tip.journal.PatternJournal;
import com.tip.market.CandleEngine;
import com.tip.market.event.CandleClosedEvent;
import com.tip.market.model.Candle;
import com.tip.patterns.breakout.BreakoutBarEvaluation;
import com.tip.patterns.breakout.BreakoutBarEvaluator;
import com.tip.patterns.breakout.BreakoutConfig;
import com.tip.patterns.breakdown.BreakdownBarEvaluation;
import com.tip.patterns.breakdown.BreakdownBarEvaluator;
import com.tip.patterns.breakdown.BreakdownConfig;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.PatternStageEvent;
import com.tip.patterns.model.PatternType;
import com.tip.patterns.consolidation.ConsolidationBarEvaluator;
import com.tip.patterns.consolidation.ConsolidationConfig;
import com.tip.patterns.engulfing.EngulfingBarEvaluator;
import com.tip.patterns.engulfing.EngulfingConfig;
import com.tip.patterns.insidebar.InsideBarBarEvaluator;
import com.tip.patterns.insidebar.InsideBarConfig;
import com.tip.patterns.pinbar.PinBarBarEvaluation;
import com.tip.patterns.pinbar.PinBarBarEvaluator;
import com.tip.patterns.pinbar.PinBarConfig;
import com.tip.patterns.structure.StructureBarEvaluator;
import com.tip.patterns.structure.StructureConfig;
import com.tip.patterns.support.SimpleBarEvaluation;
import com.tip.patterns.volumebreakout.VolumeBreakoutBarEvaluator;
import com.tip.patterns.volumebreakout.VolumeBreakoutConfig;
import com.tip.watchlist.WatchlistEntry;
import com.tip.watchlist.WatchlistRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.tip.config.AsyncConfig;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Listens for closed candles and runs all pattern families (breakout, pin-bar, engulfing,
 * inside-bar, HH/LL structure) + journal / WS.
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
    private final PatternSeriesGate seriesGate;

    public PatternEvaluator(
            PatternFeatureGuard featureGuard,
            PatternProperties patternProperties,
            CandleEngine candleEngine,
            WatchlistRepository watchlistRepository,
            ActiveInstanceStore activeInstanceStore,
            PatternJournal patternJournal,
            PatternEventPublisher patternEventPublisher,
            PatternSeriesGate seriesGate
    ) {
        this.featureGuard = featureGuard;
        this.patternProperties = patternProperties;
        this.candleEngine = candleEngine;
        this.watchlistRepository = watchlistRepository;
        this.activeInstanceStore = activeInstanceStore;
        this.patternJournal = patternJournal;
        this.patternEventPublisher = patternEventPublisher;
        this.seriesGate = seriesGate;
    }

    /**
     * Runs off the market-data callback thread so detectors + journal I/O cannot stall ticks.
     * Per-series work is still serialized by {@link PatternSeriesGate}.
     */
    @Async(AsyncConfig.PATTERN_TASK_EXECUTOR)
    @EventListener
    public void onCandleClosed(CandleClosedEvent event) {
        if (!featureGuard.isFullyEnabled()) {
            return;
        }
        String symbolId = event.instrumentKey();
        String timeframe = event.timeframe();
        log.info("Pattern eval (async) on close: {} {} bar={}",
                symbolId, timeframe, event.candle() != null ? event.candle().time() : null);
        seriesGate.run(symbolId, timeframe, () -> evaluateUnlocked(symbolId, timeframe, event.candle()));
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

            BreakoutConfig breakoutConfig = patternProperties.toBreakoutConfig();
            BreakdownConfig breakdownConfig = patternProperties.toBreakdownConfig();
            PinBarConfig pinBarConfig = patternProperties.toPinBarConfig();
            EngulfingConfig engulfingConfig = patternProperties.toEngulfingConfig();
            InsideBarConfig insideBarConfig = patternProperties.toInsideBarConfig();
            StructureConfig structureConfig = patternProperties.toStructureConfig();
            ConsolidationConfig consolidationConfig = patternProperties.toConsolidationConfig();
            VolumeBreakoutConfig volumeBreakoutConfig = patternProperties.toVolumeBreakoutConfig();
            Instant now = Instant.now();

            List<ActivePattern> open = new ArrayList<>(activeInstanceStore.getOpen(symbolId, timeframe));
            List<ActivePattern> openBreakouts = open.stream()
                    .filter(p -> p.patternType() == PatternType.BREAKOUT)
                    .toList();
            List<ActivePattern> openBreakdowns = open.stream()
                    .filter(p -> p.patternType() == PatternType.BREAKDOWN)
                    .toList();
            List<ActivePattern> openPinBars = open.stream()
                    .filter(p -> p.patternType().isPinBar())
                    .toList();
            List<ActivePattern> openEngulfing = open.stream()
                    .filter(p -> p.patternType().isEngulfing())
                    .toList();
            List<ActivePattern> openInside = open.stream()
                    .filter(p -> p.patternType() == PatternType.INSIDE_BAR)
                    .toList();
            List<ActivePattern> openStructure = open.stream()
                    .filter(p -> p.patternType().isStructure())
                    .toList();
            List<ActivePattern> openConsol = open.stream()
                    .filter(p -> p.patternType() == PatternType.CONSOLIDATION)
                    .toList();
            List<ActivePattern> openVolBo = open.stream()
                    .filter(p -> p.patternType() == PatternType.VOLUME_BREAKOUT)
                    .toList();
            List<ActivePattern> otherOpen = open.stream()
                    .filter(p -> p.patternType() != PatternType.BREAKOUT
                            && p.patternType() != PatternType.BREAKDOWN
                            && !p.patternType().isPinBar()
                            && !p.patternType().isEngulfing()
                            && p.patternType() != PatternType.INSIDE_BAR
                            && !p.patternType().isStructure()
                            && p.patternType() != PatternType.CONSOLIDATION
                            && p.patternType() != PatternType.VOLUME_BREAKOUT)
                    .toList();

            BreakoutBarEvaluation breakoutEval = BreakoutBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openBreakouts), closed, index, breakoutConfig, now
            );
            BreakdownBarEvaluation breakdownEval = BreakdownBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openBreakdowns), closed, index, breakdownConfig, now
            );
            PinBarBarEvaluation pinBarEval = PinBarBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openPinBars), closed, pinBarConfig, now
            );
            SimpleBarEvaluation engulfingEval = EngulfingBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openEngulfing), closed, engulfingConfig, now
            );
            SimpleBarEvaluation insideEval = InsideBarBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openInside), closed, insideBarConfig, now
            );
            SimpleBarEvaluation structureEval = StructureBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openStructure), closed, structureConfig, now
            );
            SimpleBarEvaluation consolEval = ConsolidationBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openConsol), closed, consolidationConfig, now
            );
            SimpleBarEvaluation volBoEval = VolumeBreakoutBarEvaluator.evaluate(
                    symbolId, timeframe, new ArrayList<>(openVolBo), closed, index, volumeBreakoutConfig, now
            );

            persistEval(breakoutEval.advanced(), breakoutEval.newlyDetected(), breakoutEval.events());
            persistEval(breakdownEval.advanced(), breakdownEval.newlyDetected(), breakdownEval.events());
            persistEval(pinBarEval.advanced(), pinBarEval.newlyDetected(), pinBarEval.events());
            persistEval(engulfingEval.advanced(), engulfingEval.newlyDetected(), engulfingEval.events());
            persistEval(insideEval.advanced(), insideEval.newlyDetected(), insideEval.events());
            persistEval(structureEval.advanced(), structureEval.newlyDetected(), structureEval.events());
            persistEval(consolEval.advanced(), consolEval.newlyDetected(), consolEval.events());
            persistEval(volBoEval.advanced(), volBoEval.newlyDetected(), volBoEval.events());

            List<ActivePattern> still = new ArrayList<>();
            still.addAll(breakoutEval.stillOpen());
            still.addAll(breakdownEval.stillOpen());
            still.addAll(pinBarEval.stillOpen());
            still.addAll(engulfingEval.stillOpen());
            still.addAll(insideEval.stillOpen());
            still.addAll(structureEval.stillOpen());
            still.addAll(consolEval.stillOpen());
            still.addAll(volBoEval.stillOpen());
            still.addAll(otherOpen);
            still = applyDurationPolicies(still, signal, timeframe, now);

            activeInstanceStore.replaceOpen(symbolId, timeframe, still);
        } catch (RuntimeException ex) {
            log.warn("Pattern evaluation failed for {} {}: {}", symbolId, timeframe, ex.toString());
        }
    }

    private void persistEval(
            List<ActivePattern> advanced,
            List<ActivePattern> newlyDetected,
            List<PatternStageEvent> allEvents
    ) {
        for (ActivePattern adv : advanced) {
            List<PatternStageEvent> stageEvents = allEvents.stream()
                    .filter(e -> e.instanceId().equals(adv.id()))
                    .toList();
            if (!stageEvents.isEmpty() || adv.isTerminal()) {
                patternJournal.persistLifecycle(adv, stageEvents);
                patternEventPublisher.publish(adv, stageEvents);
            }
        }
        for (ActivePattern detected : newlyDetected) {
            List<PatternStageEvent> all = allEvents.stream()
                    .filter(e -> e.instanceId().equals(detected.id()))
                    .toList();
            patternJournal.persistLifecycle(detected, all);
            patternEventPublisher.publish(detected, all);
        }
    }

    private List<ActivePattern> applyDurationPolicies(
            List<ActivePattern> open,
            Candle signal,
            String timeframe,
            Instant now
    ) {
        List<ActivePattern> kept = new ArrayList<>();
        for (ActivePattern p : open) {
            boolean expire = false;
            String reason = null;
            if (patternProperties.isMultiDayTimeframe(timeframe)) {
                int maxCandles = patternProperties.maxCandlesFor(timeframe);
                int maxSessions = patternProperties.maxSessionsFor(timeframe);
                if (p.durationCandles() >= maxCandles) {
                    expire = true;
                    reason = "max_candles";
                } else if (patternProperties.tracksSessionsOnClose(timeframe)
                        && p.sessionsSeen() >= maxSessions) {
                    expire = true;
                    reason = "max_sessions";
                }
            }
            if (expire) {
                List<PatternStageEvent> ev = PatternLifecycleSupport.expire(p, signal, reason, now, false);
                patternJournal.applyEvents(p, ev);
                patternEventPublisher.publish(p, ev);
            } else {
                kept.add(p);
            }
        }
        return kept;
    }
}
