package com.tip.watchlist;

import java.util.List;
import java.util.Optional;

/**
 * Persistence-agnostic watchlist store.
 * <p>
 * Query visibility (KD26):
 * <ul>
 *   <li><b>Public-active</b> APIs exclude {@link SymbolBootstrapStatus#REMOVING}
 *       (and {@code active=false} if ever set):
 *       {@link #findAllActive()}, {@link #findPrimary()}, {@link #containsSymbolId(String)},
 *       {@link #findByTradingSymbolIgnoreCase(String)}, {@link #countActive()}</li>
 *   <li><b>Internal</b> APIs may still see REMOVING rows until hard-delete:
 *       {@link #findBySymbolId(String)}</li>
 * </ul>
 */
public interface WatchlistRepository {

    /**
     * Active, non-REMOVING entries in stable insertion order.
     */
    List<WatchlistEntry> findAllActive();

    /**
     * Primary symbol for /api/market/* shim and WS default when symbolId is omitted.
     * <p>v1 in-memory: first public-active entry in LinkedHashMap insertion order.
     * Empty / all-REMOVING → {@link Optional#empty()}.
     */
    Optional<WatchlistEntry> findPrimary();

    /**
     * Lookup including REMOVING rows (still present until hard-delete).
     */
    Optional<WatchlistEntry> findBySymbolId(String symbolId);

    /**
     * True iff present, active, and bootstrapStatus != REMOVING.
     */
    boolean containsSymbolId(String symbolId);

    /**
     * Case-insensitive trading-symbol lookup among public-active entries only.
     */
    Optional<WatchlistEntry> findByTradingSymbolIgnoreCase(String tradingSymbol);

    /**
     * Insert or replace. On first insert, appends in insertion order.
     * On update: does not reorder map position and does not rewrite {@code addedAt}.
     */
    WatchlistEntry save(WatchlistEntry entry);

    /**
     * Hard-delete from the store (v1 in-memory). Returns true if the symbol was present
     * (including REMOVING).
     */
    boolean remove(String symbolId);

    /**
     * Count of public-active entries (excludes REMOVING).
     */
    int countActive();
}
