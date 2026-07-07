package com.tip.config;

import com.tip.api.websocket.LiveWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LiveWebSocketHandler liveWebSocketHandler;
    private final CorsProperties corsProperties;

    public WebSocketConfig(LiveWebSocketHandler liveWebSocketHandler, CorsProperties corsProperties) {
        this.liveWebSocketHandler = liveWebSocketHandler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(liveWebSocketHandler, "/ws/live")
                .setAllowedOrigins(corsProperties.allowedOrigins());
    }
}