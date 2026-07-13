package com.tip.journal;

import com.tip.journal.entity.PatternInstanceEntity;
import com.tip.patterns.model.ActivePattern;
import com.tip.patterns.model.ConfirmationMode;
import com.tip.patterns.model.FinalOutcome;
import com.tip.patterns.model.PatternDirection;
import com.tip.patterns.model.PatternStage;
import com.tip.patterns.model.PatternType;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;

final class PatternInstanceMapper {

    static final Set<String> OPEN_STATUSES = Set.of(
            "detected", "confirmed", "retested", "strengthened"
    );

    static final Set<String> CLOSED_STATUSES = Set.of(
            "succeeded", "failed", "expired"
    );

    private PatternInstanceMapper() {
    }

    static PatternInstanceEntity toNewEntity(ActivePattern p, Instant now) {
        PatternInstanceEntity e = new PatternInstanceEntity();
        e.setId(p.id());
        e.setSymbolId(p.symbolId());
        e.setPatternType(p.patternType().wireValue());
        e.setTimeframe(p.timeframe());
        e.setDirection(p.direction().wireValue());
        e.setStatus(p.status().wireValue());
        e.setFlagConfirmed(p.flagConfirmed());
        e.setFlagRetested(p.flagRetested());
        e.setFlagStrengthened(p.flagStrengthened());
        e.setVolumeOkAtDetect(p.volumeOkAtDetect());
        e.setReferenceLevel(p.referenceLevel());
        e.setLookbackHigh(p.lookbackHigh());
        e.setAtrAtDetect(p.atrAtDetect());
        e.setVolumeAtDetect(p.volumeAtDetect());
        e.setConfirmationModeUsed(p.confirmationModeUsed().wireValue());
        e.setEntryPrice(p.entryPrice());
        e.setStopLevel(p.stopLevel());
        e.setTargetLevel(p.targetLevel());
        e.setRetestFloor(p.retestFloor());
        e.setDetectorVersion(p.detectorVersion());
        e.setDetectedAt(p.detectedAt());
        e.setConfirmedAt(p.confirmedAt());
        e.setEndedAt(p.isTerminal() ? now : null);
        e.setDetectCandleTime(p.detectCandleTime());
        e.setSessionsSeen(p.sessionsSeen());
        e.setCreatedAt(now);
        e.setUpdatedAt(now);
        return e;
    }

    static void copyMutable(ActivePattern p, PatternInstanceEntity e, Instant now) {
        e.setStatus(p.status().wireValue());
        e.setFlagConfirmed(p.flagConfirmed());
        e.setFlagRetested(p.flagRetested());
        e.setFlagStrengthened(p.flagStrengthened());
        e.setTargetLevel(p.targetLevel());
        e.setRetestFloor(p.retestFloor());
        e.setConfirmedAt(p.confirmedAt());
        e.setSessionsSeen(p.sessionsSeen());
        e.setUpdatedAt(now);
        if (p.isTerminal()) {
            e.setEndedAt(now);
        }
    }

    static ActivePattern toDomain(PatternInstanceEntity e) {
        PatternStage status = PatternStage.valueOf(e.getStatus().toUpperCase(Locale.ROOT));
        ActivePattern.Builder b = ActivePattern.builder()
                .id(e.getId())
                .patternType(PatternType.valueOf(e.getPatternType().toUpperCase(Locale.ROOT)))
                .symbolId(e.getSymbolId())
                .timeframe(e.getTimeframe())
                .direction(PatternDirection.valueOf(e.getDirection().toUpperCase(Locale.ROOT)))
                .status(status)
                .flagConfirmed(e.isFlagConfirmed())
                .flagRetested(e.isFlagRetested())
                .flagStrengthened(e.isFlagStrengthened())
                .volumeOkAtDetect(e.isVolumeOkAtDetect())
                .confirmationModeUsed(ConfirmationMode.fromConfig(e.getConfirmationModeUsed()))
                .referenceLevel(e.getReferenceLevel())
                .lookbackHigh(e.getLookbackHigh())
                .atrAtDetect(e.getAtrAtDetect())
                .volumeAtDetect(e.getVolumeAtDetect())
                .entryPrice(e.getEntryPrice())
                .stopLevel(e.getStopLevel())
                .targetLevel(e.getTargetLevel() != null ? e.getTargetLevel() : e.getReferenceLevel())
                .retestFloor(e.getRetestFloor())
                .detectCandleTime(e.getDetectCandleTime())
                .detectedAt(e.getDetectedAt())
                .confirmedAt(e.getConfirmedAt())
                .mfePrice(e.getEntryPrice())
                .maePrice(e.getEntryPrice())
                .durationCandles(1)
                .sessionsSeen(e.getSessionsSeen())
                .detectorVersion(e.getDetectorVersion());

        ActivePattern p = b.build();
        if (status.isTerminal()) {
            FinalOutcome outcome = switch (status) {
                case SUCCEEDED -> FinalOutcome.SUCCEEDED;
                case FAILED -> FinalOutcome.FAILED;
                case EXPIRED -> FinalOutcome.EXPIRED;
                default -> FinalOutcome.EXPIRED;
            };
            p.markTerminal(outcome, status, "loaded_terminal", null);
        }
        return p;
    }
}
