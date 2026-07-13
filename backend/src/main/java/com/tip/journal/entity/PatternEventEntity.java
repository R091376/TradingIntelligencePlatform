package com.tip.journal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pattern_events")
public class PatternEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pattern_instance_id", nullable = false)
    private UUID patternInstanceId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "event_time", nullable = false)
    private Instant eventTime;

    @Column(name = "candle_time", nullable = false)
    private long candleTime;

    @Column(name = "price_at_event", nullable = false)
    private double priceAtEvent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UUID getPatternInstanceId() {
        return patternInstanceId;
    }

    public void setPatternInstanceId(UUID patternInstanceId) {
        this.patternInstanceId = patternInstanceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Instant getEventTime() {
        return eventTime;
    }

    public void setEventTime(Instant eventTime) {
        this.eventTime = eventTime;
    }

    public long getCandleTime() {
        return candleTime;
    }

    public void setCandleTime(long candleTime) {
        this.candleTime = candleTime;
    }

    public double getPriceAtEvent() {
        return priceAtEvent;
    }

    public void setPriceAtEvent(double priceAtEvent) {
        this.priceAtEvent = priceAtEvent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
