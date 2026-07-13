package com.tip.journal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "pattern_statistics")
@IdClass(PatternStatisticsEntity.Pk.class)
public class PatternStatisticsEntity {

    @Id
    @Column(name = "symbol_id")
    private String symbolId;

    @Id
    @Column(name = "pattern_type")
    private String patternType;

    @Id
    @Column(name = "timeframe")
    private String timeframe;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "fail_count", nullable = false)
    private int failCount;

    @Column(name = "expired_count", nullable = false)
    private int expiredCount;

    @Column(name = "success_rate", nullable = false)
    private double successRate;

    @Column(name = "resolved_success_rate")
    private Double resolvedSuccessRate;

    @Column(name = "avg_move_r", nullable = false)
    private double avgMoveR;

    @Column(name = "avg_duration_candles", nullable = false)
    private double avgDurationCandles;

    @Column(name = "avg_mfe_r", nullable = false)
    private double avgMfeR;

    @Column(name = "avg_mae_r", nullable = false)
    private double avgMaeR;

    @Column(name = "move_sample_size", nullable = false)
    private int moveSampleSize;

    @Column(name = "mfe_sample_size", nullable = false)
    private int mfeSampleSize;

    @Column(name = "mae_sample_size", nullable = false)
    private int maeSampleSize;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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

    public int getSampleSize() {
        return sampleSize;
    }

    public void setSampleSize(int sampleSize) {
        this.sampleSize = sampleSize;
    }

    public int getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }

    public int getFailCount() {
        return failCount;
    }

    public void setFailCount(int failCount) {
        this.failCount = failCount;
    }

    public int getExpiredCount() {
        return expiredCount;
    }

    public void setExpiredCount(int expiredCount) {
        this.expiredCount = expiredCount;
    }

    public double getSuccessRate() {
        return successRate;
    }

    public void setSuccessRate(double successRate) {
        this.successRate = successRate;
    }

    public Double getResolvedSuccessRate() {
        return resolvedSuccessRate;
    }

    public void setResolvedSuccessRate(Double resolvedSuccessRate) {
        this.resolvedSuccessRate = resolvedSuccessRate;
    }

    public double getAvgMoveR() {
        return avgMoveR;
    }

    public void setAvgMoveR(double avgMoveR) {
        this.avgMoveR = avgMoveR;
    }

    public double getAvgDurationCandles() {
        return avgDurationCandles;
    }

    public void setAvgDurationCandles(double avgDurationCandles) {
        this.avgDurationCandles = avgDurationCandles;
    }

    public double getAvgMfeR() {
        return avgMfeR;
    }

    public void setAvgMfeR(double avgMfeR) {
        this.avgMfeR = avgMfeR;
    }

    public double getAvgMaeR() {
        return avgMaeR;
    }

    public void setAvgMaeR(double avgMaeR) {
        this.avgMaeR = avgMaeR;
    }

    public int getMoveSampleSize() {
        return moveSampleSize;
    }

    public void setMoveSampleSize(int moveSampleSize) {
        this.moveSampleSize = moveSampleSize;
    }

    public int getMfeSampleSize() {
        return mfeSampleSize;
    }

    public void setMfeSampleSize(int mfeSampleSize) {
        this.mfeSampleSize = mfeSampleSize;
    }

    public int getMaeSampleSize() {
        return maeSampleSize;
    }

    public void setMaeSampleSize(int maeSampleSize) {
        this.maeSampleSize = maeSampleSize;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class Pk implements Serializable {
        private String symbolId;
        private String patternType;
        private String timeframe;

        public Pk() {
        }

        public Pk(String symbolId, String patternType, String timeframe) {
            this.symbolId = symbolId;
            this.patternType = patternType;
            this.timeframe = timeframe;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof Pk pk)) {
                return false;
            }
            return Objects.equals(symbolId, pk.symbolId)
                    && Objects.equals(patternType, pk.patternType)
                    && Objects.equals(timeframe, pk.timeframe);
        }

        @Override
        public int hashCode() {
            return Objects.hash(symbolId, patternType, timeframe);
        }
    }
}
