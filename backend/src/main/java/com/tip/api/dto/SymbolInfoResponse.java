package com.tip.api.dto;

public record SymbolInfoResponse(
        String symbol,
        String instrumentKey,
        String timeframe
) {
}