package com.tip.market.model;

public record Tick(
        String instrumentKey,
        double price,
        long volumeTradedToday,
        long timestampMs
) {
}