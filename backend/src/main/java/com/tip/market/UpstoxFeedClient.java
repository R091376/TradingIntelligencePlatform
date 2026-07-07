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
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UpstoxFeedClient {

    private static final Logger log = LoggerFactory.getLogger(UpstoxFeedClient.class);

    private final ApiClient apiClient;
    private final MarketStatusService marketStatusService;
    private MarketDataStreamerV3 streamer;
    private TickHandler tickHandler;
    private final AtomicLong tickCount = new AtomicLong();
    private volatile boolean marketInfoLogged;

    public UpstoxFeedClient(ApiClient apiClient, MarketStatusService marketStatusService) {
        this.apiClient = apiClient;
        this.marketStatusService = marketStatusService;
    }

    public void connect(String instrumentKey, TickHandler handler) {
        if (handler == null) {
            throw new IllegalArgumentException("Tick handler is required");
        }

        disconnect();
        this.tickHandler = handler;
        this.tickCount.set(0);
        this.marketInfoLogged = false;

        streamer = new MarketDataStreamerV3(apiClient, Set.of(instrumentKey), Mode.FULL);

        streamer.setOnOpenListener(new OnOpenListener() {
            @Override
            public void onOpen() {
                marketStatusService.setLiveFeedConnected(true);
                log.info("Upstox live feed connected for {}", instrumentKey);
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

    public void disconnect() {
        if (streamer != null) {
            streamer.disconnect();
            streamer = null;
        }
        tickHandler = null;
        marketStatusService.setLiveFeedConnected(false);
    }

    public long tickCount() {
        return tickCount.get();
    }

    private void handleMarketUpdate(MarketUpdateV3 update) {
        if (update.getType() == MarketUpdateV3.Type.market_info) {
            if (update.getMarketInfo() != null) {
                marketStatusService.updateFromSegmentStatus(
                        update.getMarketInfo().getSegmentStatus()
                );
                if (!marketInfoLogged) {
                    marketInfoLogged = true;
                    log.info("NSE_EQ market phase: {}", marketStatusService.getMarketPhase());
                }
            }
            return;
        }

        if (update.getFeeds() == null || update.getFeeds().isEmpty()) {
            return;
        }

        for (Map.Entry<String, MarketUpdateV3.Feed> entry : update.getFeeds().entrySet()) {
            Tick tick = extractTick(entry.getKey(), entry.getValue());
            if (tick != null && tickHandler != null) {
                tickHandler.onTick(tick);
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