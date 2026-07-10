package com.tip.api.dto;

/**
 * POST /api/watchlist body.
 * <p>
 * Provide either {@code instrumentKey} (preferred after autocomplete) or {@code symbol}
 * (trading symbol free-text). If both are present, {@code instrumentKey} wins.
 */
public record AddWatchlistRequest(
        String symbol,
        String instrumentKey
) {
}
