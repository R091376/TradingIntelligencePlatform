package com.tip.market;

import com.tip.market.model.Tick;
import com.upstox.ApiClient;
import com.upstox.feeder.MarketDataStreamerV3;
import com.upstox.feeder.MarketUpdateV3;
import com.upstox.feeder.constants.Mode;
import com.upstox.feeder.listener.OnCloseListener;
import com.upstox.feeder.listener.OnErrorListener;
import com.upstox.feeder.listener.OnMarketUpdateV3Listener;
import com.upstox.feeder.listener.OnOpenListener;
import com.upstox.feeder.listener.OnReconnectingListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiFunction;

/**
 * Long-lived Upstox Market Data Feed V3 client (KD23).
 * <p>
 * One {@link MarketDataStreamerV3} per process while the live feed is enabled.
 * Dynamic add/remove uses {@code subscribe}/{@code unsubscribe} only — never
 * disconnect+recreate per instrument. Local {@link #subscribedKeys} is the
 * source of truth; any new streamer is constructed with the full local set.
 * <p>
 * On SDK subscribe/unsubscribe failure, the local set is rolled back so a later
 * retry can re-issue the operation.
 */
@Component
public class UpstoxFeedClient {

    private static final Logger log = LoggerFactory.getLogger(UpstoxFeedClient.class);

    @FunctionalInterface
    interface StreamerFactory extends BiFunction<ApiClient, Set<String>, MarketDataStreamerV3> {
    }

    private final ApiClient apiClient;
    private final MarketStatusService marketStatusService;
    private final StreamerFactory streamerFactory;

    private final Set<String> subscribedKeys = ConcurrentHashMap.newKeySet();
    private final AtomicLong tickCount = new AtomicLong();

    private final Object lock = new Object();
    private MarketDataStreamerV3 streamer;
    private volatile TickHandler tickHandler;
    private volatile boolean marketInfoLogged;

    @Autowired
    public UpstoxFeedClient(ApiClient apiClient, MarketStatusService marketStatusService) {
        this(apiClient, marketStatusService,
                (client, keys) -> new MarketDataStreamerV3(client, keys, Mode.FULL));
    }

    /** Package-visible for tests that inject a mock streamer factory. */
    UpstoxFeedClient(
            ApiClient apiClient,
            MarketStatusService marketStatusService,
            StreamerFactory streamerFactory
    ) {
        this.apiClient = apiClient;
        this.marketStatusService = marketStatusService;
        this.streamerFactory = Objects.requireNonNull(streamerFactory, "streamerFactory");
    }

    /**
     * Ensure a streamer is connected and subscribed to {@code instrumentKeys}.
     * If already live, only subscribes missing keys (no reconnect).
     */
    public void connect(Set<String> instrumentKeys, TickHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Tick handler is required");
        }
        Objects.requireNonNull(instrumentKeys, "instrumentKeys");

        synchronized (lock) {
            this.tickHandler = handler;

            if (streamer != null) {
                Set<String> missing = new HashSet<>(instrumentKeys);
                missing.removeAll(subscribedKeys);
                if (!missing.isEmpty()) {
                    subscribeInternal(missing);
                }
                return;
            }

            tickCount.set(0);
            marketInfoLogged = false;
            subscribedKeys.clear();
            subscribedKeys.addAll(instrumentKeys);
            createAndConnectStreamer();
        }
    }

    /** Convenience for single-key connect. */
    public void connect(String instrumentKey, TickHandler handler) {
        connect(Set.of(instrumentKey), handler);
    }

    /**
     * Add keys to the local set and, if the streamer is live, subscribe without recreate.
     * Local set is only retained when the SDK call succeeds (or streamer is not yet up).
     */
    public void subscribe(Set<String> instrumentKeys) {
        if (instrumentKeys == null || instrumentKeys.isEmpty()) {
            return;
        }
        synchronized (lock) {
            subscribeInternal(instrumentKeys);
        }
    }

    public void subscribe(String instrumentKey) {
        if (instrumentKey == null || instrumentKey.isBlank()) {
            return;
        }
        subscribe(Set.of(instrumentKey));
    }

    /**
     * Remove keys from the local set and, if the streamer is live, unsubscribe without recreate.
     * Streamer stays connected even if the last key is removed (idle) so the next add is cheap.
     * On SDK failure, keys are restored to the local set.
     */
    public void unsubscribe(Set<String> instrumentKeys) {
        if (instrumentKeys == null || instrumentKeys.isEmpty()) {
            return;
        }
        synchronized (lock) {
            Set<String> toRemove = new HashSet<>(instrumentKeys);
            toRemove.retainAll(subscribedKeys);
            if (toRemove.isEmpty()) {
                return;
            }
            subscribedKeys.removeAll(toRemove);
            if (streamer != null) {
                try {
                    streamer.unsubscribe(Set.copyOf(toRemove));
                    log.info("Upstox live feed unsubscribed: {}", toRemove);
                } catch (Exception e) {
                    // Roll back so local set still matches what we believe the SDK holds.
                    subscribedKeys.addAll(toRemove);
                    log.warn("Upstox unsubscribe failed for {} (local set restored): {}",
                            toRemove, e.toString());
                }
            }
        }
    }

    public void unsubscribe(String instrumentKey) {
        if (instrumentKey == null || instrumentKey.isBlank()) {
            return;
        }
        unsubscribe(Set.of(instrumentKey));
    }

    /**
     * Full shutdown: disconnect streamer and clear local subscription set.
     */
    public void disconnect() {
        synchronized (lock) {
            if (streamer != null) {
                try {
                    streamer.disconnect();
                } catch (Exception e) {
                    log.debug("Upstox disconnect error: {}", e.toString());
                }
                streamer = null;
            }
            subscribedKeys.clear();
            tickHandler = null;
            marketStatusService.setLiveFeedConnected(false);
        }
    }

    /** Snapshot of keys TIP wants subscribed (source of truth after successful ops). */
    public Set<String> subscribedKeys() {
        return Collections.unmodifiableSet(new HashSet<>(subscribedKeys));
    }

    public long tickCount() {
        return tickCount.get();
    }

    public boolean isStreamerLive() {
        synchronized (lock) {
            return streamer != null;
        }
    }

    private void subscribeInternal(Set<String> keys) {
        Set<String> toAdd = new HashSet<>();
        for (String key : keys) {
            if (key != null && !key.isBlank() && !subscribedKeys.contains(key)) {
                toAdd.add(key);
            }
        }
        if (toAdd.isEmpty()) {
            return;
        }

        // No live streamer yet: record intent so the next createAndConnectStreamer uses full set.
        if (streamer == null) {
            subscribedKeys.addAll(toAdd);
            return;
        }

        try {
            streamer.subscribe(Set.copyOf(toAdd), Mode.FULL);
            subscribedKeys.addAll(toAdd);
            log.info("Upstox live feed subscribed: {}", toAdd);
        } catch (Exception e) {
            // Do not keep keys that the SDK rejected — allows a later retry to re-send.
            log.warn("Upstox subscribe failed for {} (not retained locally): {}",
                    toAdd, e.toString());
        }
    }

    private void createAndConnectStreamer() {
        // Local set is source of truth on (re)build (KD23).
        streamer = streamerFactory.apply(apiClient, Set.copyOf(subscribedKeys));

        streamer.setOnOpenListener(new OnOpenListener() {
            @Override
            public void onOpen() {
                marketStatusService.setLiveFeedConnected(true);
                // Belt-and-suspenders: re-apply full local set after open (SDK reconnect path
                // also restores its internal map; this covers intentional rebuilds).
                reapplyLocalSubscriptionsOnOpen();
                log.info("Upstox live feed connected for keys={}", subscribedKeys());
            }
        });

        streamer.setOnCloseListener(new OnCloseListener() {
            @Override
            public void onClose(int code, String reason) {
                marketStatusService.setLiveFeedConnected(false);
                log.debug("Upstox live feed closed: {} {}", code, reason);
            }
        });

        streamer.setOnMarketUpdateListener(new OnMarketUpdateV3Listener() {
            @Override
            public void onUpdate(MarketUpdateV3 marketUpdate) {
                handleMarketUpdate(marketUpdate);
            }
        });

        streamer.setOnErrorListener(new OnErrorListener() {
            @Override
            public void onError(Throwable throwable) {
                marketStatusService.setLiveFeedConnected(false);
                if (marketStatusService.getMarketPhase() != MarketPhase.CLOSED) {
                    log.error("Upstox live feed error", throwable);
                } else {
                    log.debug("Upstox live feed error while market closed: {}", throwable.getMessage());
                }
            }
        });

        streamer.setOnReconnectingListener(new OnReconnectingListener() {
            @Override
            public void onReconnecting(String message) {
                if (marketStatusService.getMarketPhase() != MarketPhase.CLOSED) {
                    log.warn("Upstox live feed reconnecting: {}", message);
                }
            }
        });

        streamer.autoReconnect(true, 5, 10);
        streamer.connect();
    }

    /**
     * Re-subscribe full local set after open. Best-effort; failures leave local set intact
     * (constructor already passed initial keys).
     */
    private void reapplyLocalSubscriptionsOnOpen() {
        Set<String> keys = Set.copyOf(subscribedKeys);
        if (keys.isEmpty() || streamer == null) {
            return;
        }
        try {
            streamer.subscribe(keys, Mode.FULL);
        } catch (Exception e) {
            log.debug("Re-apply subscriptions on open skipped/failed: {}", e.toString());
        }
    }

    private void handleMarketUpdate(MarketUpdateV3 update) {
        if (update.getType() == MarketUpdateV3.Type.market_info) {
            if (update.getMarketInfo() != null) {
                marketStatusService.updateFromSegmentStatus(
                        update.getMarketInfo().getSegmentStatus()
                );
                if (!marketInfoLogged) {
                    marketInfoLogged = true;
                    log.info("Market phase from feed: {}", marketStatusService.getMarketPhase());
                }
            }
            return;
        }

        if (update.getFeeds() == null || update.getFeeds().isEmpty()) {
            return;
        }

        TickHandler handler = tickHandler;
        for (Map.Entry<String, MarketUpdateV3.Feed> entry : update.getFeeds().entrySet()) {
            Tick tick = extractTick(entry.getKey(), entry.getValue());
            if (tick != null && handler != null) {
                handler.onTick(tick);
                long count = tickCount.incrementAndGet();
                if (count <= 3 || count % 200 == 0) {
                    log.debug("Tick #{}: {} ltp={} vtt={}",
                            count, tick.instrumentKey(), tick.price(), tick.volumeTradedToday());
                }
            }
        }
    }

    private Tick extractTick(String instrumentKey, MarketUpdateV3.Feed feed) {
        if (feed.getFullFeed() != null && feed.getFullFeed().getMarketFF() != null) {
            MarketUpdateV3.MarketFullFeed marketFeed = feed.getFullFeed().getMarketFF();
            MarketUpdateV3.LTPC ltpc = marketFeed.getLtpc();
            if (ltpc == null) {
                return null;
            }
            return new Tick(
                    instrumentKey,
                    ltpc.getLtp(),
                    marketFeed.getVtt(),
                    ltpc.getLtt()
            );
        }

        if (feed.getFirstLevelWithGreeks() != null) {
            MarketUpdateV3.FirstLevelWithGreeks greeksFeed = feed.getFirstLevelWithGreeks();
            MarketUpdateV3.LTPC ltpc = greeksFeed.getLtpc();
            if (ltpc == null) {
                return null;
            }
            return new Tick(
                    instrumentKey,
                    ltpc.getLtp(),
                    greeksFeed.getVtt(),
                    ltpc.getLtt()
            );
        }

        if (feed.getLtpc() != null) {
            MarketUpdateV3.LTPC ltpc = feed.getLtpc();
            return new Tick(
                    instrumentKey,
                    ltpc.getLtp(),
                    0L,
                    ltpc.getLtt()
            );
        }

        return null;
    }
}
