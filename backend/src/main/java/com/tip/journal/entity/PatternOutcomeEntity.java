package com.tip.journal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pattern_outcomes")
public class PatternOutcomeEntity {

    @Id
    @Column(name = "pattern_instance_id")
    private UUID patternInstanceId;

    @Column(name = "final_outcome", nullable = false)
    private String finalOutcome;

    @Column(name = "duration_candles", nullable = false)
    private int durationCandles;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Column(name = "max_favorable_r")
    private Double maxFavorableR;

    @Column(name = "max_adverse_r")
    private Double maxAdverseR;

    @Column(name = "max_favorable_price")
    private Double maxFavorablePrice;

    @Column(name = "max_adverse_price")
    private Double maxAdversePrice;

    @Column(name = "move_r")
    private Double moveR;

    @Column(name = "end_price")
    private Double endPrice;

    @Column(name = "reason")
    private String reason;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    public UUID getPatternInstanceId() {
        return patternInstanceId;
    }

    public void setPatternInstanceId(UUID patternInstanceId) {
        this.patternInstanceId = patternInstanceId;
    }

    public String getFinalOutcome() {
        return finalOutcome;
    }

    public void setFinalOutcome(String finalOutcome) {
        this.finalOutcome = finalOutcome;
    }

    public int getDurationCandles() {
        return durationCandles;
    }

    public void setDurationCandles(int durationCandles) {
        this.durationCandles = durationCandles;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Double getMaxFavorableR() {
        return maxFavorableR;
    }

    public void setMaxFavorableR(Double maxFavorableR) {
        this.maxFavorableR = maxFavorableR;
    }

    public Double getMaxAdverseR() {
        return maxAdverseR;
    }

    public void setMaxAdverseR(Double maxAdverseR) {
        this.maxAdverseR = maxAdverseR;
    }

    public Double getMaxFavorablePrice() {
        return maxFavorablePrice;
    }

    public void setMaxFavorablePrice(Double maxFavorablePrice) {
        this.maxFavorablePrice = maxFavorablePrice;
    }

    public Double getMaxAdversePrice() {
        return maxAdversePrice;
    }

    public void setMaxAdversePrice(Double maxAdversePrice) {
        this.maxAdversePrice = maxAdversePrice;
    }

    public Double getMoveR() {
        return moveR;
    }

    public void setMoveR(Double moveR) {
        this.moveR = moveR;
    }

    public Double getEndPrice() {
        return endPrice;
    }

    public void setEndPrice(Double endPrice) {
        this.endPrice = endPrice;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(Instant closedAt) {
        this.closedAt = closedAt;
    }
}
