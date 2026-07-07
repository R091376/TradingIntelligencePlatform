package com.tip.api.websocket;

import com.tip.market.event.MarketPhaseChangedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MarketStatusBroadcaster {

    private final LiveWebSocketHandler webSocketHandler;

    public MarketStatusBroadcaster(LiveWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @EventListener
    public void onMarketPhaseChanged(MarketPhaseChangedEvent event) {
        webSocketHandler.broadcastAll(Map.of(
                "type", "market_status",
                "marketPhase", event.phase().name().toLowerCase()
        ));
    }
}