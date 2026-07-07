package com.tip.api.websocket;

public record LiveSubscription(
        String symbolId,
        String timeframe
) {
    public String key() {
        return symbolId + "|" + timeframe;
    }
}