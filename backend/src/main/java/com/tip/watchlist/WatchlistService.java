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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Per-symbolId locks for add/remove isolation. */
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            WatchlistProperties watchlistProperties,
            InstrumentMasterCache instrumentMasterCache,
            MarketBootstrapService marketBootstrapService,
            MarketDataProvider marketDataProvider,
            CandleEngine candleEngine,
            LiveCandleBroadcaster liveCandleBroadcaster,
            LiveWebSocketHandler liveWebSocketHandler
    ) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistProperties = watchlistProperties;
        this.instrumentMasterCache = instrumentMasterCache;
        this.marketBootstrapService = marketBootstrapService;
        this.marketDataProvider = marketDataProvider;
        this.candleEngine = candleEngine;
        this.liveCandleBroadcaster = liveCandleBroadcaster;
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
     * Blocking add by trading symbol: resolve → hard-max → soft-warn → PENDING →
     * bootstrap → subscribe → return final entry (READY or FAILED).
     */
    public WatchlistEntry add(String tradingSymbol) {
        String input = validateTradingSymbolInput(tradingSymbol);

        ResolvedInstrument resolved;
        try {
            resolved = instrumentMasterCache.resolve(input);
        } catch (InstrumentNotFoundException e) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Unknown trading symbol: " + input
            );
        }

        String symbolId = resolved.instrumentKey();
        Object lock = lockFor(symbolId);
        synchronized (lock) {
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

            MarketBootstrapService.BootstrapSymbolResult result =
                    marketBootstrapService.bootstrapSymbol(saved);

            Optional<WatchlistEntry> after = watchlistRepository.findBySymbolId(symbolId);
            if (after.isEmpty() || after.get().bootstrapStatus() == SymbolBootstrapStatus.REMOVING
                    || result.status() == SymbolBootstrapStatus.REMOVING) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Symbol was removed during bootstrap: " + symbolId
                );
            }

            // Subscribe live feed for READY or FAILED so ticks can still arrive
            if (result.status() == SymbolBootstrapStatus.READY
                    || result.status() == SymbolBootstrapStatus.FAILED) {
                try {
                    marketDataProvider.subscribeInstruments(Set.of(symbolId));
                } catch (Exception e) {
                    log.warn("Failed to subscribe live feed for {}: {}", symbolId, e.getMessage());
                }
            }

            WatchlistEntry finalEntry = watchlistRepository.findBySymbolId(symbolId).orElse(after.get());
            log.info("Watchlist add complete: {} status={}", symbolId, finalEntry.bootstrapStatus());
            return finalEntry;
        }
    }

    /**
     * Remove: mark REMOVING → unsubscribe → engine.evict → broadcaster.evictThrottleKeys
     * → notify WS → hard-delete.
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
        log.info("Watchlist remove complete (hard-delete): {}", symbolId);
    }

    private Object lockFor(String symbolId) {
        return locks.computeIfAbsent(symbolId, k -> new Object());
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
