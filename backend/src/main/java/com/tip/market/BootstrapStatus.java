package com.tip.market;

/**
 * Process / market-session bootstrap status (session recovery and feed connectivity).
 * <p>
 * Distinct from {@link com.tip.watchlist.SymbolBootstrapStatus}, which tracks per-watchlist-symbol
 * lifecycle including {@code REMOVING} — do not interchange the two.
 */
public enum BootstrapStatus {
    PENDING,
    READY,
    FAILED
}
