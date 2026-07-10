package com.tip.watchlist;

import com.tip.config.WatchlistProperties;
import com.tip.instrument.InstrumentMasterCache;
import com.tip.instrument.InstrumentNotFoundException;
import com.tip.instrument.ResolvedInstrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Watchlist orchestration: seed on empty startup, later add/remove (PR4).
 * <p>
 * {@link #ensureSeeded()} is key-authoritative: {@code tip.watchlist.seed-instrument-keys}
 * wins over master resolve; master is used for display enrich and for seeds without a pinned key.
 */
@Service
public class WatchlistService {

    private static final Logger log = LoggerFactory.getLogger(WatchlistService.class);

    private final WatchlistRepository watchlistRepository;
    private final WatchlistProperties watchlistProperties;
    private final InstrumentMasterCache instrumentMasterCache;

    public WatchlistService(
            WatchlistRepository watchlistRepository,
            WatchlistProperties watchlistProperties,
            InstrumentMasterCache instrumentMasterCache
    ) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistProperties = watchlistProperties;
        this.instrumentMasterCache = instrumentMasterCache;
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

    /**
     * Resolve and insert a single seed entry as PENDING.
     *
     * @return true if inserted
     */
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
        // Case-insensitive fallback for config drift
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
