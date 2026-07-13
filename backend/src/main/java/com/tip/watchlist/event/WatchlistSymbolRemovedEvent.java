package com.tip.watchlist.event;

/**
 * Fired after candle/feed cleanup when a symbol is removed from the watchlist.
 */
public record WatchlistSymbolRemovedEvent(String symbolId) {
}
