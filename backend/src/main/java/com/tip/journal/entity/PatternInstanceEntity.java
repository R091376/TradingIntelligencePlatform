package com.tip.journal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pattern_instances")
public class PatternInstanceEntity {

    @Id
    private UUID id;

    @Column(name = "symbol_id", nullable = false)
    private String symbolId;

    @Column(name = "pattern_type", nullable = false)
    private String patternType;

    @Column(name = "timeframe", nullable = false)
    private String timeframe;

    @Column(name = "direction", nullable = false)
    private String direction;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "flag_confirmed", nullable = false)
    private boolean flagConfirmed;

    @Column(name = "flag_retested", nullable = false)
    private boolean flagRetested;

    @Column(name = "flag_strengthened", nullable = false)
    private boolean flagStrengthened;

    @Column(name = "volume_ok_at_detect", nullable = false)
    private boolean volumeOkAtDetect;

    @Column(name = "reference_level", nullable = false)
    private double referenceLevel;

    @Column(name = "lookback_high", nullable = false)
    private double lookbackHigh;

    @Column(name = "atr_at_detect", nullable = false)
    private double atrAtDetect;

    @Column(name = "volume_at_detect", nullable = false)
    private long volumeAtDetect;

    @Column(name = "confirmation_mode_used")
    private String confirmationModeUsed;

    @Column(name = "entry_price", nullable = false)
    private double entryPrice;

    @Column(name = "stop_level", nullable = false)
    private double stopLevel;

    @Column(name = "target_level")
    private Double targetLevel;

    @Column(name = "retest_floor")
    private Double retestFloor;

    @Column(name = "detector_version", nullable = false)
    private String detectorVersion;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "detect_candle_time", nullable = false)
    private long detectCandleTime;

    @Column(name = "sessions_seen", nullable = false)
    private int sessionsSeen;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSymbolId() {
        return symbolId;
    }

    public void setSymbolId(String symbolId) {
        this.symbolId = symbolId;
    }

    public String getPatternType() {
        return patternType;
    }

    public void setPatternType(String patternType) {
        this.patternType = patternType;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        this.direction = direction;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isFlagConfirmed() {
        return flagConfirmed;
    }

    public void setFlagConfirmed(boolean flagConfirmed) {
        this.flagConfirmed = flagConfirmed;
    }

    public boolean isFlagRetested() {
        return flagRetested;
    }

    public void setFlagRetested(boolean flagRetested) {
        this.flagRetested = flagRetested;
    }

    public boolean isFlagStrengthened() {
        return flagStrengthened;
    }

    public void setFlagStrengthened(boolean flagStrengthened) {
        this.flagStrengthened = flagStrengthened;
    }

    public boolean isVolumeOkAtDetect() {
        return volumeOkAtDetect;
    }

    public void setVolumeOkAtDetect(boolean volumeOkAtDetect) {
        this.volumeOkAtDetect = volumeOkAtDetect;
    }

    public double getReferenceLevel() {
        return referenceLevel;
    }

    public void setReferenceLevel(double referenceLevel) {
        this.referenceLevel = referenceLevel;
    }

    public double getLookbackHigh() {
        return lookbackHigh;
    }

    public void setLookbackHigh(double lookbackHigh) {
        this.lookbackHigh = lookbackHigh;
    }

    public double getAtrAtDetect() {
        return atrAtDetect;
    }

    public void setAtrAtDetect(double atrAtDetect) {
        this.atrAtDetect = atrAtDetect;
    }

    public long getVolumeAtDetect() {
        return volumeAtDetect;
    }

    public void setVolumeAtDetect(long volumeAtDetect) {
        this.volumeAtDetect = volumeAtDetect;
    }

    public String getConfirmationModeUsed() {
        return confirmationModeUsed;
    }

    public void setConfirmationModeUsed(String confirmationModeUsed) {
        this.confirmationModeUsed = confirmationModeUsed;
    }

    public double getEntryPrice() {
        return entryPrice;
    }

    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }

    public double getStopLevel() {
        return stopLevel;
    }

    public void setStopLevel(double stopLevel) {
        this.stopLevel = stopLevel;
    }

    public Double getTargetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(Double targetLevel) {
        this.targetLevel = targetLevel;
    }

    public Double getRetestFloor() {
        return retestFloor;
    }

    public void setRetestFloor(Double retestFloor) {
        this.retestFloor = retestFloor;
    }

    public String getDetectorVersion() {
        return detectorVersion;
    }

    public void setDetectorVersion(String detectorVersion) {
        this.detectorVersion = detectorVersion;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public long getDetectCandleTime() {
        return detectCandleTime;
    }

    public void setDetectCandleTime(long detectCandleTime) {
        this.detectCandleTime = detectCandleTime;
    }

    public int getSessionsSeen() {
        return sessionsSeen;
    }

    public void setSessionsSeen(int sessionsSeen) {
        this.sessionsSeen = sessionsSeen;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
