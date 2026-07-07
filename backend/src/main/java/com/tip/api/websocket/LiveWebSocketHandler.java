package com.tip.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tip.api.dto.SubscribeMessage;
import com.tip.config.MarketProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LiveWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(LiveWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final MarketProperties marketProperties;
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, LiveSubscription> subscriptions = new ConcurrentHashMap<>();

    public LiveWebSocketHandler(ObjectMapper objectMapper, MarketProperties marketProperties) {
        this.objectMapper = objectMapper;
        this.marketProperties = marketProperties;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Live WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        SubscribeMessage subscribeMessage = objectMapper.readValue(message.getPayload(), SubscribeMessage.class);

        if (!"subscribe".equals(subscribeMessage.type())) {
            sendError(session, "Unsupported message type: " + subscribeMessage.type());
            return;
        }

        String timeframe = subscribeMessage.timeframe() != null
                ? subscribeMessage.timeframe()
                : marketProperties.defaultTimeframe();
        String symbolId = subscribeMessage.symbolId() != null
                ? subscribeMessage.symbolId()
                : marketProperties.defaultInstrumentKey();

        if (!marketProperties.defaultInstrumentKey().equals(symbolId)) {
            sendError(session, "Unknown symbolId for MVP1: " + symbolId);
            return;
        }

        if (!marketProperties.defaultTimeframe().equals(timeframe)) {
            sendError(session, "Unsupported timeframe for MVP1: " + timeframe);
            return;
        }

        LiveSubscription subscription = new LiveSubscription(symbolId, timeframe);
        subscriptions.put(session.getId(), subscription);
        log.info("Live WebSocket subscribed: session={} {}", session.getId(), subscription.key());

        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "subscribed",
                "symbolId", symbolId,
                "timeframe", timeframe
        ))));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscriptions.remove(session.getId());
        log.info("Live WebSocket disconnected: {} ({})", session.getId(), status);
    }

    public void broadcastAll(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.error("Failed to serialize WebSocket payload", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        sessions.forEach((sessionId, session) -> {
            if (session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.warn("Failed to send to session {}", sessionId, e);
                }
            }
        });
    }

    public void broadcast(String symbolId, String timeframe, Object payload) {
        String targetKey = symbolId + "|" + timeframe;
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.error("Failed to serialize WebSocket payload", e);
            return;
        }

        TextMessage message = new TextMessage(json);
        subscriptions.forEach((sessionId, subscription) -> {
            if (!subscription.key().equals(targetKey)) {
                return;
            }
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    synchronized (session) {
                        session.sendMessage(message);
                    }
                } catch (IOException e) {
                    log.warn("Failed to send to session {}", sessionId, e);
                }
            }
        });
    }

    private void sendError(WebSocketSession session, String error) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(Map.of(
                "type", "error",
                "message", error
        ))));
    }
}