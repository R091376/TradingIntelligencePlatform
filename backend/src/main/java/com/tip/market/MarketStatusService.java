package com.tip.market;

import com.tip.market.event.MarketPhaseChangedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.upstox.feeder.MarketUpdateV3;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MarketStatusService {

    private final ApplicationEventPublisher eventPublisher;
    private final AtomicReference<MarketPhase> marketPhase =
            new AtomicReference<>(NseMarketClock.phaseFromClock());
    private final AtomicReference<BootstrapStatus> bootstrapStatus =
            new AtomicReference<>(BootstrapStatus.PENDING);
    private final AtomicReference<String> bootstrapError = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastSeededAt = new AtomicReference<>(null);
    private final AtomicReference<Boolean> liveFeedConnected = new AtomicReference<>(false);
    private final AtomicReference<Integer> seededCandleCount = new AtomicReference<>(0);

    public MarketStatusService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public MarketPhase getMarketPhase() {
        return marketPhase.get();
    }

    public BootstrapStatus getBootstrapStatus() {
        return bootstrapStatus.get();
    }

    public String getBootstrapError() {
        return bootstrapError.get();
    }

    public Instant getLastSeededAt() {
        return lastSeededAt.get();
    }

    public boolean isLiveFeedConnected() {
        return liveFeedConnected.get();
    }

    public int getSeededCandleCount() {
        return seededCandleCount.get();
    }

    public void setBootstrapPending() {
        bootstrapStatus.set(BootstrapStatus.PENDING);
        bootstrapError.set(null);
    }

    public void setBootstrapReady(int candleCount) {
        bootstrapStatus.set(BootstrapStatus.READY);
        bootstrapError.set(null);
        seededCandleCount.set(candleCount);
        lastSeededAt.set(Instant.now());
    }

    public void setBootstrapFailed(String error) {
        bootstrapStatus.set(BootstrapStatus.FAILED);
        bootstrapError.set(error);
    }

    public void setLiveFeedConnected(boolean connected) {
        liveFeedConnected.set(connected);
    }

    public void updateFromSegmentStatus(Map<String, MarketUpdateV3.MarketStatus> segmentStatus) {
        if (segmentStatus == null) {
            return;
        }

        MarketUpdateV3.MarketStatus nseEq = segmentStatus.get("NSE_EQ");
        if (nseEq == null) {
            return;
        }

        setMarketPhase(mapSegmentStatus(nseEq));
    }

    public void refreshPhaseFromClock() {
        setMarketPhase(NseMarketClock.phaseFromClock());
    }

    private MarketPhase mapSegmentStatus(MarketUpdateV3.MarketStatus status) {
        return switch (status) {
            case NORMAL_OPEN -> MarketPhase.OPEN;
            case PRE_OPEN_START, PRE_OPEN_END -> MarketPhase.PRE_OPEN;
            case NORMAL_CLOSE, CLOSING_START, CLOSING_END -> MarketPhase.CLOSED;
        };
    }

    private void setMarketPhase(MarketPhase phase) {
        MarketPhase previous = marketPhase.getAndSet(phase);
        if (previous != phase) {
            eventPublisher.publishEvent(new MarketPhaseChangedEvent(phase));
        }
    }
}