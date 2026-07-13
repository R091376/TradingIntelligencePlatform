package com.tip.watchlist;

import java.util.List;
import java.util.Optional;

/**
 * Persistence-agnostic watchlist store.
 * <p>
 * Query visibility (KD26):
 * <ul>
 *   <li><b>Public-active</b> APIs exclude {@link SymbolBootstrapStatus#REMOVING}
 *       (and inactive rows):
 *       {@link #findAllActive()}, {@link #findPrimary()}, {@link #containsSymbolId(String)},
 *       {@link #findByTradingSymbolIgnoreCase(String)}, {@link #countActive()}</li>
 *   <li><b>Internal</b> {@link #findBySymbolId(String)} may still see REMOVING rows
 *       until remove completes</li>
 * </ul>
 * <p>
 * Trading-symbol uniqueness is a service-layer invariant: at most one public-active
 * entry per trading symbol (case-insensitive).
 */
public interface WatchlistRepository {

    /** Public-active entries in stable insertion order ({@code added_at} for Postgres). */
    List<WatchlistEntry> findAllActive();

    /**
     * Primary symbol for /api/market/* shim and WS default when symbolId is omitted.
     * Empty / all-REMOVING → {@link Optional#empty()}.
     */
    Optional<WatchlistEntry> findPrimary();

    /** Lookup including REMOVING rows (still present until remove completes). */
    Optional<WatchlistEntry> findBySymbolId(String symbolId);

    /** True iff present, active, and bootstrapStatus != REMOVING. */
    boolean containsSymbolId(String symbolId);

    /** Case-insensitive trading-symbol lookup among public-active entries only. */
    Optional<WatchlistEntry> findByTradingSymbolIgnoreCase(String tradingSymbol);

    /**
     * Insert or replace. On update of an existing active row, does not rewrite
     * {@code addedAt}. Does not enforce unique trading symbols.
     */
    WatchlistEntry save(WatchlistEntry entry);

    /**
     * Remove from the active watchlist. Postgres soft-deletes; in-memory hard-deletes.
     * Returns true if the symbol was present (including REMOVING).
     */
    boolean remove(String symbolId);

    /** Count of public-active entries (excludes REMOVING). */
    int countActive();
}
