package com.tip.watchlist;

/**
 * Bootstrap lifecycle for a watchlist symbol.
 * <p>
 * {@code REMOVING} is an internal mid-delete state: public-active queries exclude it,
 * while {@link WatchlistRepository#findBySymbolId(String)} still returns it until hard-delete.
 */
public enum SymbolBootstrapStatus {
    PENDING,
    READY,
    FAILED,
    REMOVING
}
