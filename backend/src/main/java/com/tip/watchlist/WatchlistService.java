package com.tip.watchlist;

import com.tip.api.websocket.LiveCandleBroadcaster;
import com.tip.api.websocket.LiveWebSocketHandler;
import com.tip.config.WatchlistProperties;
import com.tip.instrument.InstrumentMasterCache;
import com.tip.instrument.InstrumentNotFoundException;
import com.tip.instrument.ResolvedInstrument;
import com.tip.market.CandleEngine;
import com.tip.market.MarketBootstrapService;
import com.tip.market.MarketDataProvider;
import com.tip.watchlist.event.WatchlistSymbolRemovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Watchlist orchestration: startup seed ({@link #ensureSeeded}), blocking add, and remove.
 * <p>
 * {@link #ensureSeeded()} is key-authoritative: {@code tip.watchlist.seed-instrument-keys}
 * wins over master resolve; master is used for display enrich and for seeds without a pinned key.
 * <p>
 * Locking (PR4 review fixes):
 * <ul>
 *   <li>{@code addCapacityLock} — short critical section for hard-max check + PENDING insert
 *       (OI-1: race-safe capacity across concurrent multi-symbol POSTs)</li>
 *   <li>per-{@code symbolId} lock — duplicate checks, insert handoff, remove, post-bootstrap;
 *       <b>not</b> held across {@code bootstrapSymbol} so DELETE can mark REMOVING mid-seed (OI-2)</li>
 *   <li>Lock order when both needed: {@code addCapacityLock} then per-symbol lock</li>
 * </ul>
 */
@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);
    private static final int MAX_SYMBOL_LENGTH = 64;

    private final WatchlistRepository watchlistRepository;
    private final WatchlistProperties watchlistProperties;
    private final InstrumentMasterCache instrumentMasterCache;
    private final MarketBootstrapService marketBootstrapService;
    private final MarketDataProvider marketDataProvider;
    private final CandleEngine candleEngine;
    private final LiveCandleBroadcaster liveCandleBroadcaster;
    private final LiveWebSocketHandler liveWebSocketHandler;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Global mutex for hard-max capacity reservation (countActive + insert PENDING).
     * Not held during bootstrap I/O.
     */
    private final Object addCapacityLock = new Object();

    /** Per-symbolId locks for add/remove isolation (not held across bootstrap). */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            WatchlistProperties watchlistProperties,
            InstrumentMasterCache instrumentMasterCache,
            MarketBootstrapService marketBootstrapService,
            MarketDataProvider marketDataProvider,
            CandleEngine candleEngine,
            LiveCandleBroadcaster liveCandleBroadcaster,
            LiveWebSocketHandler liveWebSocketHandler,
            ApplicationEventPublisher eventPublisher
    ) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistProperties = watchlistProperties;
        this.instrumentMasterCache = instrumentMasterCache;
        this.marketBootstrapService = marketBootstrapService;
        this.marketDataProvider = marketDataProvider;
        this.candleEngine = candleEngine;
        this.liveCandleBroadcaster = liveCandleBroadcaster;
        this.eventPublisher = eventPublisher;
        this.liveWebSocketHandler = liveWebSocketHandler;
    }

    /**
     * If the watchlist is empty, insert configured seed symbols in order.
     * Skips unresolved symbols with ERROR log; does not abort remaining seeds.
     * First successful insert becomes primary (normally NIFTY 50 index).
     */
    public void ensureSeeded() {
        if (watchlistRepository.countActive() > 0) {
            log.debug("Watchlist already has {} active symbol(s); skip seed",
                    watchlistRepository.countActive());
            return;
        }

        var seedSymbols = watchlistProperties.seedSymbols();
        if (seedSymbols.isEmpty()) {
            log.warn("tip.watchlist.seed-symbols is empty; watchlist remains empty");
            return;
        }

        log.info("Seeding watchlist with {} configured symbol(s)", seedSymbols.size());
        int inserted = 0;
        for (String tradingSymbol : seedSymbols) {
            try {
                if (insertSeed(tradingSymbol)) {
                    inserted++;
                }
            } catch (Exception e) {
                log.error("Failed to seed watchlist symbol '{}': {}", tradingSymbol, e.toString());
            }
        }
        log.info("Watchlist seed complete: inserted={}/{}", inserted, seedSymbols.size());
    }

    public List<WatchlistEntry> listActive() {
        return watchlistRepository.findAllActive();
    }

    /**
     * Blocking add by trading symbol only (compat wrapper).
     */
    public WatchlistEntry add(String tradingSymbol) {
        return add(tradingSymbol, null);
    }

    /**
     * Blocking add: resolve by {@code instrumentKey} (preferred) or trading {@code symbol} →
     * hard-max reserve → PENDING → bootstrap (unlocked) → subscribe → return final entry
     * (READY or FAILED).
     */
    public WatchlistEntry add(String tradingSymbol, String instrumentKey) {
        ResolvedInstrument resolved = resolveForAdd(tradingSymbol, instrumentKey);

        String symbolId = resolved.instrumentKey();
        Object symbolLock = lockFor(symbolId);

        // OI-1 + OI-2: reserve capacity + insert PENDING under short locks; do not hold during bootstrap.
        WatchlistEntry saved = reserveAndInsertPending(resolved, symbolId, symbolLock);

        // Bootstrap outside all locks so same-symbol DELETE can mark REMOVING (cooperative cancel).
        MarketBootstrapService.BootstrapSymbolResult result;
        try {
            result = marketBootstrapService.bootstrapSymbol(saved);
        } catch (Exception e) {
            // OI-3: never leave a permanent PENDING zombie after the request fails.
            log.error("Unexpected bootstrap failure for {}: {}", symbolId, e.toString());
            markFailedIfStillPresent(symbolId, symbolLock,
                    "Bootstrap failed unexpectedly: " + (e.getMessage() != null ? e.getMessage() : e.toString()));
            result = null;
        }

        // Post-bootstrap: re-check concurrent remove, subscribe if still present.
        synchronized (symbolLock) {
            Optional<WatchlistEntry> after = watchlistRepository.findBySymbolId(symbolId);
            if (after.isEmpty() || after.get().bootstrapStatus() == SymbolBootstrapStatus.REMOVING
                    || (result != null && result.status() == SymbolBootstrapStatus.REMOVING)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Symbol was removed during bootstrap: " + symbolId
                );
            }

            WatchlistEntry entry = after.get();

            // READY/FAILED: ensure streamer exists (connect if recovery never did) and keys are subscribed.
            SymbolBootstrapStatus status = entry.bootstrapStatus();
            if (status == SymbolBootstrapStatus.READY || status == SymbolBootstrapStatus.FAILED) {
                try {
                    marketBootstrapService.ensureLiveFeedConnected();
                } catch (Exception e) {
                    log.warn("Failed to ensure live feed after add for {}: {}", symbolId, e.getMessage());
                }
            } else if (status == SymbolBootstrapStatus.PENDING) {
                // Safety net: should not happen if bootstrap always writes READY/FAILED.
                markFailedUnlocked(entry, "Bootstrap completed without status update");
                entry = watchlistRepository.findBySymbolId(symbolId).orElse(entry);
            }

            log.info("Watchlist add complete: {} status={}", symbolId, entry.bootstrapStatus());
            return entry;
        }
    }

    /**
     * Hard-max check + soft-warn + PENDING insert under global capacity lock then per-symbol lock.
     * Lock order: {@code addCapacityLock} → per-symbol.
     */
    private WatchlistEntry reserveAndInsertPending(
            ResolvedInstrument resolved,
            String symbolId,
            Object symbolLock
    ) {
        synchronized (addCapacityLock) {
            synchronized (symbolLock) {
                if (watchlistRepository.containsSymbolId(symbolId)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Symbol already on watchlist: " + resolved.tradingSymbol()
                    );
                }
                Optional<WatchlistEntry> byTs =
                        watchlistRepository.findByTradingSymbolIgnoreCase(resolved.tradingSymbol());
                if (byTs.isPresent()) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Symbol already on watchlist: " + byTs.get().tradingSymbol()
                    );
                }
                Optional<WatchlistEntry> existing = watchlistRepository.findBySymbolId(symbolId);
                if (existing.isPresent()) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Symbol already on watchlist: " + resolved.tradingSymbol()
                    );
                }

                int active = watchlistRepository.countActive();
                int hardMax = watchlistProperties.hardMaxSize();
                if (active >= hardMax) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT,
                            "Watchlist is full (max " + hardMax + " symbols)"
                    );
                }

                int softWarn = watchlistProperties.softWarnSize();
                if (active >= softWarn) {
                    log.warn("Watchlist soft-warn threshold reached: active={} softWarn={} hardMax={}",
                            active, softWarn, hardMax);
                }

                WatchlistEntry pending = new WatchlistEntry(
                        symbolId,
                        resolved.tradingSymbol(),
                        resolved.exchange(),
                        resolved.segment(),
                        resolved.instrumentType(),
                        resolved.displayName(),
                        Instant.now(),
                        true,
                        SymbolBootstrapStatus.PENDING,
                        null
                );
                WatchlistEntry saved = watchlistRepository.save(pending);
                log.info("Watchlist add PENDING: {} ({})", resolved.tradingSymbol(), symbolId);
                return saved;
            }
        }
    }

    /**
     * OI-3: mark FAILED if entry still exists and is not REMOVING (does not resurrect deleted rows).
     */
    private void markFailedIfStillPresent(String symbolId, Object symbolLock, String error) {
        synchronized (symbolLock) {
            Optional<WatchlistEntry> opt = watchlistRepository.findBySymbolId(symbolId);
            if (opt.isEmpty()) {
                return;
            }
            WatchlistEntry e = opt.get();
            if (e.bootstrapStatus() == SymbolBootstrapStatus.REMOVING) {
                return;
            }
            markFailedUnlocked(e, error);
        }
    }

    private void markFailedUnlocked(WatchlistEntry e, String error) {
        watchlistRepository.save(new WatchlistEntry(
                e.symbolId(),
                e.tradingSymbol(),
                e.exchange(),
                e.segment(),
                e.instrumentType(),
                e.displayName(),
                e.addedAt(),
                e.active(),
                SymbolBootstrapStatus.FAILED,
                error
        ));
        log.warn("Watchlist entry marked FAILED: {} — {}", e.symbolId(), error);
    }

    /**
     * Remove: mark REMOVING → unsubscribe → engine.evict → broadcaster.evictThrottleKeys
     * → notify WS → hard-delete.
     * <p>
     * Per-symbol lock is short (status + cleanup); concurrent {@code add} releases its lock
     * during bootstrap so this can mark REMOVING and trigger cooperative cancel.
     */
    public void remove(String symbolId) {
        if (symbolId == null || symbolId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Symbol not on watchlist");
        }

        Object lock = lockFor(symbolId);
        synchronized (lock) {
            Optional<WatchlistEntry> opt = watchlistRepository.findBySymbolId(symbolId);
            if (opt.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Symbol not on watchlist: " + symbolId
                );
            }

            WatchlistEntry entry = opt.get();
            if (entry.bootstrapStatus() == SymbolBootstrapStatus.REMOVING) {
                finishRemove(symbolId);
                return;
            }

            WatchlistEntry removing = new WatchlistEntry(
                    entry.symbolId(),
                    entry.tradingSymbol(),
                    entry.exchange(),
                    entry.segment(),
                    entry.instrumentType(),
                    entry.displayName(),
                    entry.addedAt(),
                    true,
                    SymbolBootstrapStatus.REMOVING,
                    entry.bootstrapError()
            );
            watchlistRepository.save(removing);
            log.info("Watchlist remove REMOVING: {}", symbolId);

            finishRemove(symbolId);
        }
    }

    private void finishRemove(String symbolId) {
        try {
            marketDataProvider.unsubscribeInstruments(Set.of(symbolId));
        } catch (Exception e) {
            log.warn("unsubscribe failed for {}: {}", symbolId, e.getMessage());
        }

        candleEngine.evict(symbolId);
        liveCandleBroadcaster.evictThrottleKeys(symbolId);
        liveWebSocketHandler.notifySymbolRemoved(symbolId);

        watchlistRepository.remove(symbolId);
        eventPublisher.publishEvent(new WatchlistSymbolRemovedEvent(symbolId));
        log.info("Watchlist remove complete: {}", symbolId);
    }

    private Object lockFor(String symbolId) {
        return locks.computeIfAbsent(symbolId, k -> new Object());
    }

    private ResolvedInstrument resolveForAdd(String tradingSymbol, String instrumentKey) {
        boolean hasKey = instrumentKey != null && !instrumentKey.isBlank();
        boolean hasSymbol = tradingSymbol != null && !tradingSymbol.isBlank();

        if (!hasKey && !hasSymbol) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "symbol or instrumentKey is required"
            );
        }

        if (hasKey) {
            String key = validateInstrumentKeyInput(instrumentKey);
            return instrumentMasterCache.findByInstrumentKey(key)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Unknown instrument key: " + key
                    ));
        }

        String input = validateTradingSymbolInput(tradingSymbol);
        try {
            return instrumentMasterCache.resolve(input);
        } catch (InstrumentNotFoundException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Unknown trading symbol: " + input
            );
        }
    }

    private static String validateInstrumentKeyInput(String instrumentKey) {
        String trimmed = instrumentKey.trim();
        if (trimmed.length() > 128) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "instrumentKey exceeds max length of 128"
            );
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "instrumentKey contains invalid characters"
                );
            }
        }
        return trimmed;
    }

    private static String validateTradingSymbolInput(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol is required");
        }
        String trimmed = tradingSymbol.trim();
        if (trimmed.length() > MAX_SYMBOL_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "symbol exceeds max length of " + MAX_SYMBOL_LENGTH
            );
        }
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isISOControl(trimmed.charAt(i))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "symbol contains invalid characters");
            }
        }
        return trimmed;
    }

    // --- seed helpers (PR3) ---

    private boolean insertSeed(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            log.error("Skipping blank seed symbol");
            return false;
        }

        String pinnedKey = lookupSeedInstrumentKey(tradingSymbol);
        if (pinnedKey != null && !pinnedKey.isBlank()) {
            ResolvedInstrument enrich = tryResolve(tradingSymbol);
            WatchlistEntry entry = buildEntryFromPinnedKey(pinnedKey, tradingSymbol, enrich);
            watchlistRepository.save(entry);
            log.info("Seeded watchlist entry (pinned key): {} → {}", tradingSymbol, pinnedKey);
            return true;
        }

        try {
            ResolvedInstrument resolved = instrumentMasterCache.resolve(tradingSymbol);
            WatchlistEntry entry = fromResolved(resolved);
            watchlistRepository.save(entry);
            log.info("Seeded watchlist entry (master): {} → {}",
                    tradingSymbol, resolved.instrumentKey());
            return true;
        } catch (InstrumentNotFoundException e) {
            log.error("Skipping seed symbol unresolved (no pin, master miss): {}", tradingSymbol);
            return false;
        }
    }

    private String lookupSeedInstrumentKey(String tradingSymbol) {
        var keys = watchlistProperties.seedInstrumentKeys();
        if (keys == null || keys.isEmpty()) {
            return null;
        }
        String direct = keys.get(tradingSymbol);
        if (direct != null) {
            return direct;
        }
        for (var e : keys.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(tradingSymbol.trim())) {
                return e.getValue();
            }
        }
        return null;
    }

    private ResolvedInstrument tryResolve(String tradingSymbol) {
        try {
            return instrumentMasterCache.resolve(tradingSymbol);
        } catch (InstrumentNotFoundException e) {
            return null;
        } catch (Exception e) {
            log.debug("Optional master enrich failed for {}: {}", tradingSymbol, e.toString());
            return null;
        }
    }

    private static WatchlistEntry fromResolved(ResolvedInstrument r) {
        return new WatchlistEntry(
                r.instrumentKey(),
                r.tradingSymbol(),
                r.exchange() != null ? r.exchange() : "NSE",
                r.segment(),
                r.instrumentType(),
                r.displayName() != null ? r.displayName() : r.tradingSymbol(),
                Instant.now(),
                true,
                SymbolBootstrapStatus.PENDING,
                null
        );
    }

    private static WatchlistEntry buildEntryFromPinnedKey(
            String instrumentKey,
            String tradingSymbol,
            ResolvedInstrument enrich
    ) {
        if (enrich != null) {
            return new WatchlistEntry(
                    instrumentKey,
                    enrich.tradingSymbol() != null ? enrich.tradingSymbol() : tradingSymbol,
                    enrich.exchange() != null ? enrich.exchange() : "NSE",
                    enrich.segment() != null ? enrich.segment() : segmentFromKey(instrumentKey),
                    enrich.instrumentType() != null ? enrich.instrumentType() : typeFromKey(instrumentKey),
                    enrich.displayName() != null ? enrich.displayName() : tradingSymbol,
                    Instant.now(),
                    true,
                    SymbolBootstrapStatus.PENDING,
                    null
            );
        }
        return new WatchlistEntry(
                instrumentKey,
                tradingSymbol,
                "NSE",
                segmentFromKey(instrumentKey),
                typeFromKey(instrumentKey),
                tradingSymbol,
                Instant.now(),
                true,
                SymbolBootstrapStatus.PENDING,
                null
        );
    }

    private static String segmentFromKey(String instrumentKey) {
        if (instrumentKey != null && instrumentKey.startsWith("NSE_INDEX")) {
            return "NSE_INDEX";
        }
        return "NSE_EQ";
    }

    private static String typeFromKey(String instrumentKey) {
        if (instrumentKey != null && instrumentKey.startsWith("NSE_INDEX")) {
            return "INDEX";
        }
        return "EQ";
    }
}
