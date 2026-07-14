package com.tip.patterns.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory working set for one open (or just-closed) pattern instance.
 * Pure domain — no Spring / JPA.
 */
public final class ActivePattern {

    private final UUID id;
    private final PatternType patternType;
    private final String symbolId;
    private final String timeframe;
    private final PatternDirection direction;

    private PatternStage status;
    private boolean flagConfirmed;
    private boolean flagRetested;
    private boolean flagStrengthened;
    private boolean volumeOkAtDetect;
    private ConfirmationMode confirmationModeUsed;

    private final double referenceLevel;
    private final double lookbackHigh;
    private final double atrAtDetect;
    private final long volumeAtDetect;
    private final double entryPrice;
    private final double stopLevel;
    private double targetLevel;
    private Double retestFloor;

    private final long detectCandleTime;
    private final Instant detectedAt;
    private Instant confirmedAt;

    private double mfePrice;
    private double maePrice;
    private int durationCandles;
    private int sessionsSeen;
    private final String detectorVersion;

    private boolean terminal;
    private FinalOutcome finalOutcome;
    private String terminalReason;
    private Double endPrice;
    /** When true, journal must write NULL MFE/MAE/move and exclude from averages. */
    private boolean suppressExcursionStats;

    private ActivePattern(Builder b) {
        this.id = b.id;
        this.patternType = b.patternType;
        this.symbolId = b.symbolId;
        this.timeframe = b.timeframe;
        this.direction = b.direction;
        this.status = b.status;
        this.flagConfirmed = b.flagConfirmed;
        this.flagRetested = b.flagRetested;
        this.flagStrengthened = b.flagStrengthened;
        this.volumeOkAtDetect = b.volumeOkAtDetect;
        this.confirmationModeUsed = b.confirmationModeUsed;
        this.referenceLevel = b.referenceLevel;
        this.lookbackHigh = b.lookbackHigh;
        this.atrAtDetect = b.atrAtDetect;
        this.volumeAtDetect = b.volumeAtDetect;
        this.entryPrice = b.entryPrice;
        this.stopLevel = b.stopLevel;
        this.targetLevel = b.targetLevel;
        this.retestFloor = b.retestFloor;
        this.detectCandleTime = b.detectCandleTime;
        this.detectedAt = b.detectedAt;
        this.confirmedAt = b.confirmedAt;
        this.mfePrice = b.mfePrice;
        this.maePrice = b.maePrice;
        this.durationCandles = b.durationCandles;
        this.sessionsSeen = b.sessionsSeen;
        this.detectorVersion = b.detectorVersion;
        this.terminal = b.terminal;
        this.finalOutcome = b.finalOutcome;
        this.terminalReason = b.terminalReason;
        this.endPrice = b.endPrice;
        this.suppressExcursionStats = b.suppressExcursionStats;
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID id() {
        return id;
    }

    public PatternType patternType() {
        return patternType;
    }

    public String symbolId() {
        return symbolId;
    }

    public String timeframe() {
        return timeframe;
    }

    public PatternDirection direction() {
        return direction;
    }

    public PatternStage status() {
        return status;
    }

    public boolean flagConfirmed() {
        return flagConfirmed;
    }

    public boolean flagRetested() {
        return flagRetested;
    }

    public boolean flagStrengthened() {
        return flagStrengthened;
    }

    public boolean volumeOkAtDetect() {
        return volumeOkAtDetect;
    }

    public ConfirmationMode confirmationModeUsed() {
        return confirmationModeUsed;
    }

    public double referenceLevel() {
        return referenceLevel;
    }

    public double lookbackHigh() {
        return lookbackHigh;
    }

    public double atrAtDetect() {
        return atrAtDetect;
    }

    public long volumeAtDetect() {
        return volumeAtDetect;
    }

    public double entryPrice() {
        return entryPrice;
    }

    public double stopLevel() {
        return stopLevel;
    }

    public double targetLevel() {
        return targetLevel;
    }

    public Double retestFloor() {
        return retestFloor;
    }

    public long detectCandleTime() {
        return detectCandleTime;
    }

    public Instant detectedAt() {
        return detectedAt;
    }

    public Instant confirmedAt() {
        return confirmedAt;
    }

    public double mfePrice() {
        return mfePrice;
    }

    public double maePrice() {
        return maePrice;
    }

    public int durationCandles() {
        return durationCandles;
    }

    public int sessionsSeen() {
        return sessionsSeen;
    }

    public String detectorVersion() {
        return detectorVersion;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public FinalOutcome finalOutcome() {
        return finalOutcome;
    }

    public String terminalReason() {
        return terminalReason;
    }

    public Double endPrice() {
        return endPrice;
    }

    public boolean suppressExcursionStats() {
        return suppressExcursionStats;
    }

    public void setFlagConfirmed(boolean flagConfirmed) {
        this.flagConfirmed = flagConfirmed;
    }

    public void setFlagRetested(boolean flagRetested) {
        this.flagRetested = flagRetested;
    }

    public void setFlagStrengthened(boolean flagStrengthened) {
        this.flagStrengthened = flagStrengthened;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public void setTargetLevel(double targetLevel) {
        this.targetLevel = targetLevel;
    }

    public void setRetestFloor(Double retestFloor) {
        this.retestFloor = retestFloor;
    }

    public void setMfePrice(double mfePrice) {
        this.mfePrice = mfePrice;
    }

    public void setMaePrice(double maePrice) {
        this.maePrice = maePrice;
    }

    public void setDurationCandles(int durationCandles) {
        this.durationCandles = durationCandles;
    }

    public void setSessionsSeen(int sessionsSeen) {
        this.sessionsSeen = sessionsSeen;
    }

    public void setStatus(PatternStage status) {
        this.status = status;
    }

    public void markTerminal(FinalOutcome outcome, PatternStage stage, String reason, Double endPrice) {
        markTerminal(outcome, stage, reason, endPrice, false);
    }

    public void markTerminal(
            FinalOutcome outcome,
            PatternStage stage,
            String reason,
            Double endPrice,
            boolean suppressExcursionStats
    ) {
        this.terminal = true;
        this.finalOutcome = Objects.requireNonNull(outcome);
        this.status = stage;
        this.terminalReason = reason;
        this.endPrice = endPrice;
        this.suppressExcursionStats = suppressExcursionStats;
    }

    /**
     * Display status from flags (PI-22). Terminal statuses left as-is.
     */
    public void refreshDisplayStatus() {
        if (terminal) {
            return;
        }
        if (flagStrengthened) {
            status = PatternStage.STRENGTHENED;
        } else if (flagRetested) {
            status = PatternStage.RETESTED;
        } else if (flagConfirmed) {
            status = PatternStage.CONFIRMED;
        } else {
            status = PatternStage.DETECTED;
        }
    }

    /**
     * Absolute price extreme that is favorable for this direction.
     * Extremes tracked: {@code mfePrice}=max high, {@code maePrice}=min low.
     * LONG: max high; SHORT: min low.
     */
    public double favorableExtremePrice() {
        return direction == PatternDirection.SHORT ? maePrice : mfePrice;
    }

    /**
     * Absolute price extreme that is adverse for this direction.
     * LONG: min low; SHORT: max high.
     */
    public double adverseExtremePrice() {
        return direction == PatternDirection.SHORT ? mfePrice : maePrice;
    }

    /**
     * Max favorable excursion in R. Extremes: {@code mfePrice}=max high, {@code maePrice}=min low.
     * LONG: up from entry; SHORT: down from entry.
     */
    public double maxFavorableR() {
        if (atrAtDetect <= 0) {
            return 0;
        }
        if (direction == PatternDirection.SHORT) {
            return (entryPrice - maePrice) / atrAtDetect;
        }
        return (mfePrice - entryPrice) / atrAtDetect;
    }

    /**
     * Max adverse excursion in R. LONG: down from entry; SHORT: up from entry.
     */
    public double maxAdverseR() {
        if (atrAtDetect <= 0) {
            return 0;
        }
        if (direction == PatternDirection.SHORT) {
            return (mfePrice - entryPrice) / atrAtDetect;
        }
        return (entryPrice - maePrice) / atrAtDetect;
    }

    public Double moveR() {
        if (endPrice == null || atrAtDetect <= 0) {
            return null;
        }
        if (direction == PatternDirection.SHORT) {
            return (entryPrice - endPrice) / atrAtDetect;
        }
        return (endPrice - entryPrice) / atrAtDetect;
    }

    public static final class Builder {
        private UUID id = UUID.randomUUID();
        private PatternType patternType = PatternType.BREAKOUT;
        private String symbolId;
        private String timeframe;
        private PatternDirection direction = PatternDirection.LONG;
        private PatternStage status = PatternStage.DETECTED;
        private boolean flagConfirmed;
        private boolean flagRetested;
        private boolean flagStrengthened;
        private boolean volumeOkAtDetect;
        private ConfirmationMode confirmationModeUsed = ConfirmationMode.BOTH;
        private double referenceLevel;
        private double lookbackHigh;
        private double atrAtDetect;
        private long volumeAtDetect;
        private double entryPrice;
        private double stopLevel;
        private double targetLevel;
        private Double retestFloor;
        private long detectCandleTime;
        private Instant detectedAt = Instant.now();
        private Instant confirmedAt;
        private double mfePrice;
        private double maePrice;
        private int durationCandles = 1;
        private int sessionsSeen;
        private String detectorVersion = "breakout-v1";
        private boolean terminal;
        private FinalOutcome finalOutcome;
        private String terminalReason;
        private Double endPrice;
        private boolean suppressExcursionStats;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder patternType(PatternType patternType) {
            this.patternType = patternType;
            return this;
        }

        public Builder symbolId(String symbolId) {
            this.symbolId = symbolId;
            return this;
        }

        public Builder timeframe(String timeframe) {
            this.timeframe = timeframe;
            return this;
        }

        public Builder direction(PatternDirection direction) {
            this.direction = direction;
            return this;
        }

        public Builder status(PatternStage status) {
            this.status = status;
            return this;
        }

        public Builder flagConfirmed(boolean flagConfirmed) {
            this.flagConfirmed = flagConfirmed;
            return this;
        }

        public Builder flagRetested(boolean flagRetested) {
            this.flagRetested = flagRetested;
            return this;
        }

        public Builder flagStrengthened(boolean flagStrengthened) {
            this.flagStrengthened = flagStrengthened;
            return this;
        }

        public Builder volumeOkAtDetect(boolean volumeOkAtDetect) {
            this.volumeOkAtDetect = volumeOkAtDetect;
            return this;
        }

        public Builder confirmationModeUsed(ConfirmationMode confirmationModeUsed) {
            this.confirmationModeUsed = confirmationModeUsed;
            return this;
        }

        public Builder referenceLevel(double referenceLevel) {
            this.referenceLevel = referenceLevel;
            return this;
        }

        public Builder lookbackHigh(double lookbackHigh) {
            this.lookbackHigh = lookbackHigh;
            return this;
        }

        public Builder atrAtDetect(double atrAtDetect) {
            this.atrAtDetect = atrAtDetect;
            return this;
        }

        public Builder volumeAtDetect(long volumeAtDetect) {
            this.volumeAtDetect = volumeAtDetect;
            return this;
        }

        public Builder entryPrice(double entryPrice) {
            this.entryPrice = entryPrice;
            return this;
        }

        public Builder stopLevel(double stopLevel) {
            this.stopLevel = stopLevel;
            return this;
        }

        public Builder targetLevel(double targetLevel) {
            this.targetLevel = targetLevel;
            return this;
        }

        public Builder retestFloor(Double retestFloor) {
            this.retestFloor = retestFloor;
            return this;
        }

        public Builder detectCandleTime(long detectCandleTime) {
            this.detectCandleTime = detectCandleTime;
            return this;
        }

        public Builder detectedAt(Instant detectedAt) {
            this.detectedAt = detectedAt;
            return this;
        }

        public Builder confirmedAt(Instant confirmedAt) {
            this.confirmedAt = confirmedAt;
            return this;
        }

        public Builder mfePrice(double mfePrice) {
            this.mfePrice = mfePrice;
            return this;
        }

        public Builder maePrice(double maePrice) {
            this.maePrice = maePrice;
            return this;
        }

        public Builder durationCandles(int durationCandles) {
            this.durationCandles = durationCandles;
            return this;
        }

        public Builder sessionsSeen(int sessionsSeen) {
            this.sessionsSeen = sessionsSeen;
            return this;
        }

        public Builder detectorVersion(String detectorVersion) {
            this.detectorVersion = detectorVersion;
            return this;
        }

        public ActivePattern build() {
            Objects.requireNonNull(symbolId, "symbolId");
            Objects.requireNonNull(timeframe, "timeframe");
            Objects.requireNonNull(detectedAt, "detectedAt");
            if (mfePrice == 0 && maePrice == 0 && entryPrice != 0) {
                // leave as set by caller; detect path sets both to entry range
            }
            return new ActivePattern(this);
        }
    }
}
