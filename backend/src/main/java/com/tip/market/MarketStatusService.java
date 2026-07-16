package com.tip.market;

import com.tip.market.event.MarketPhaseChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import com.upstox.feeder.MarketUpdateV3;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class MarketStatusService {

    private static final Logger log = LoggerFactory.getLogger(MarketStatusService.class);

    static final String SEGMENT_NSE_EQ = "NSE_EQ";
    static final String SEGMENT_NSE_INDEX = "NSE_INDEX";

    /** Prefer Upstox market_info over wall-clock while feed phase is this fresh. */
    private static final Duration FEED_PHASE_FRESHNESS = Duration.ofMinutes(2);

    private final ApplicationEventPublisher eventPublisher;
    private final AtomicReference<MarketPhase> marketPhase =
            new AtomicReference<>(NseMarketClock.phaseFromClock());
    private final AtomicReference<BootstrapStatus> bootstrapStatus =
            new AtomicReference<>(BootstrapStatus.PENDING);
    private final AtomicReference<String> bootstrapError = new AtomicReference<>(null);
    private final AtomicReference<Instant> lastSeededAt = new AtomicReference<>(null);
    private final AtomicReference<Boolean> liveFeedConnected = new AtomicReference<>(false);
    private final AtomicReference<Integer> seededCandleCount = new AtomicReference<>(0);
    /** Last time phase was set from feed {@code market_info} (null = never / only clock). */
    private final AtomicReference<Instant> lastFeedPhaseAt = new AtomicReference<>(null);

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

    /**
     * Prefer {@code NSE_EQ} when present; else {@code NSE_INDEX}; else keep clock phase (KD24).
     * Feed-sourced phases are preferred over wall-clock while {@link #isLiveFeedConnected()}
     * and this update is recent (see {@link #refreshPhaseFromClock()}).
     */
    public void updateFromSegmentStatus(Map<String, MarketUpdateV3.MarketStatus> segmentStatus) {
        MarketUpdateV3.MarketStatus status = selectSegmentStatus(segmentStatus);
        if (status == null) {
            return;
        }
        lastFeedPhaseAt.set(Instant.now());
        setMarketPhase(mapSegmentStatus(status));
    }

    /**
     * Segment preference for feed market_info (KD24).
     * <ul>
     *   <li>NSE_EQ only → EQ</li>
     *   <li>NSE_INDEX only → INDEX</li>
     *   <li>Both → prefer NSE_EQ</li>
     *   <li>Neither / null → null (no update)</li>
     * </ul>
     */
    static MarketUpdateV3.MarketStatus selectSegmentStatus(
            Map<String, MarketUpdateV3.MarketStatus> segmentStatus
    ) {
        if (segmentStatus == null || segmentStatus.isEmpty()) {
            return null;
        }
        MarketUpdateV3.MarketStatus nseEq = segmentStatus.get(SEGMENT_NSE_EQ);
        if (nseEq != null) {
            return nseEq;
        }
        return segmentStatus.get(SEGMENT_NSE_INDEX);
    }

    /**
     * Reconcile phase from wall clock + holiday calendar.
     * <p>
     * When the live feed is connected and recently supplied {@code market_info}, keep the feed
     * phase for OPEN/PRE_OPEN so clock/holiday mismatch does not flap. Still force CLOSED when
     * the clock says the session is over (feed stuck OPEN after close).
     */
    public void refreshPhaseFromClock() {
        MarketPhase clockPhase = NseMarketClock.phaseFromClock();
        Instant feedAt = lastFeedPhaseAt.get();
        boolean feedFresh = isLiveFeedConnected()
                && feedAt != null
                && feedAt.isAfter(Instant.now().minus(FEED_PHASE_FRESHNESS));

        if (feedFresh) {
            // Always allow clock to force session end / holiday close.
            if (clockPhase == MarketPhase.CLOSED) {
                log.info("Phase clock: forcing CLOSED (feed was fresh but wall-clock session ended/holiday)");
                setMarketPhase(MarketPhase.CLOSED);
            } else {
                log.debug("Phase clock: keeping feed phase {} (clock={})", getMarketPhase(), clockPhase);
            }
            return;
        }
        setMarketPhase(clockPhase);
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
            log.info("Market phase changed: {} → {}", previous, phase);
            eventPublisher.publishEvent(new MarketPhaseChangedEvent(phase));
        }
    }
}
