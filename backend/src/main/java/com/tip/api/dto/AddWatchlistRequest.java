package com.tip.api.dto;

/**
 * POST /api/watchlist body. Trading symbol only (not instrument key).
 */
public record AddWatchlistRequest(
        String symbol
) {
}
