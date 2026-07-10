package com.tip.api.websocket;

import com.tip.api.dto.CandleDto;
import com.tip.api.dto.LiveCandleMessage;
import com.tip.market.event.CandleClosedEvent;
import com.tip.market.event.CandleUpdatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveCandleBroadcaster {

    private final LiveWebSocketHandler webSocketHandler;
    private final long throttleMs;
    private final Map<String, Long> lastUpdateSentAt = new ConcurrentHashMap<>();

    public LiveCandleBroadcaster(
            LiveWebSocketHandler webSocketHandler,
            @Value("${tip.market.candle-update-throttle-ms:1000}") long throttleMs
    ) {
        this.webSocketHandler = webSocketHandler;
        this.throttleMs = throttleMs;
    }

    @EventListener
    public void onCandleUpdated(CandleUpdatedEvent event) {
        if (event.isFinal()) {
            return;
        }

        String streamKey = event.instrumentKey() + "|" + event.timeframe();
        long now = System.currentTimeMillis();
        Long lastSent = lastUpdateSentAt.get(streamKey);
        if (lastSent != null && now - lastSent < throttleMs) {
            return;
        }
        lastUpdateSentAt.put(streamKey, now);

        LiveCandleMessage message = LiveCandleMessage.update(
                event.instrumentKey(),
                event.timeframe(),
                CandleDto.from(event.candle())
        );
        webSocketHandler.broadcast(event.instrumentKey(), event.timeframe(), message);
    }

    @EventListener
    public void onCandleClosed(CandleClosedEvent event) {
        String streamKey = event.instrumentKey() + "|" + event.timeframe();
        lastUpdateSentAt.remove(streamKey);

        LiveCandleMessage message = LiveCandleMessage.closed(
                event.instrumentKey(),
                event.timeframe(),
                CandleDto.from(event.candle())
        );
        webSocketHandler.broadcast(event.instrumentKey(), event.timeframe(), message);
    }

    /**
     * Drop throttle timestamps for all timeframes of an instrument (on watchlist remove).
     */
    public void evictThrottleKeys(String instrumentKey) {
        if (instrumentKey == null || instrumentKey.isBlank()) {
            return;
        }
        String prefix = instrumentKey + "|";
        lastUpdateSentAt.keySet().removeIf(k -> k.startsWith(prefix));
    }
}