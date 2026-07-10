package com.tip.watchlist;

/**
 * Per-watchlist-symbol bootstrap lifecycle (resolve → seed → ready / failed / removing).
 * <p>
 * Distinct from {@link com.tip.market.BootstrapStatus}, which tracks overall market-session
 * recovery status for the process — do not interchange the two.
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
