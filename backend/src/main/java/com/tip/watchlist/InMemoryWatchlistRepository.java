package com.tip.watchlist;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Thread-safe ordered watchlist store backed by a {@link LinkedHashMap}.
 * <p>
 * Insertion order defines primary ({@link #findPrimary()}) and is never derived from
 * {@code addedAt}. On update, existing {@code addedAt} and map position are preserved;
 * callers supply {@code addedAt} on first insert.
 * <p>
 * Trading-symbol index is last-writer-wins; uniqueness among non-REMOVING entries is a
 * service-layer invariant (see {@link WatchlistRepository}).
 */
@Component
@Primary
public class InMemoryWatchlistRepository implements WatchlistRepository {

    private final Object lock = new Object();
    private final LinkedHashMap<String, WatchlistEntry> byId = new LinkedHashMap<>();
    private final Map<String, String> tradingSymbolIndex = new HashMap<>();

    private static boolean isPublicActive(WatchlistEntry e) {
        return e.active() && e.bootstrapStatus() != SymbolBootstrapStatus.REMOVING;
    }

    private static String normalizeTradingSymbol(String tradingSymbol) {
        return tradingSymbol == null ? null : tradingSymbol.trim().toUpperCase(Locale.ROOT);
    }

    @Override
    public List<WatchlistEntry> findAllActive() {
        synchronized (lock) {
            return byId.values().stream().filter(InMemoryWatchlistRepository::isPublicActive).toList();
        }
    }

    @Override
    public Optional<WatchlistEntry> findPrimary() {
        synchronized (lock) {
            return byId.values().stream().filter(InMemoryWatchlistRepository::isPublicActive).findFirst();
        }
    }

    @Override
    public Optional<WatchlistEntry> findBySymbolId(String symbolId) {
        if (symbolId == null) {
            return Optional.empty();
        }
        synchronized (lock) {
            return Optional.ofNullable(byId.get(symbolId));
        }
    }

    @Override
    public boolean containsSymbolId(String symbolId) {
        if (symbolId == null) {
            return false;
        }
        synchronized (lock) {
            WatchlistEntry e = byId.get(symbolId);
            return e != null && isPublicActive(e);
        }
    }

    @Override
    public Optional<WatchlistEntry> findByTradingSymbolIgnoreCase(String tradingSymbol) {
        String key = normalizeTradingSymbol(tradingSymbol);
        if (key == null || key.isEmpty()) {
            return Optional.empty();
        }
        synchronized (lock) {
            String symbolId = tradingSymbolIndex.get(key);
            if (symbolId == null) {
                return Optional.empty();
            }
            WatchlistEntry e = byId.get(symbolId);
            if (e == null || !isPublicActive(e)) {
                return Optional.empty();
            }
            return Optional.of(e);
        }
    }

    @Override
    public WatchlistEntry save(WatchlistEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.symbolId(), "symbolId");
        Objects.requireNonNull(entry.tradingSymbol(), "tradingSymbol");
        if (entry.symbolId().isBlank()) {
            throw new IllegalArgumentException("symbolId must not be blank");
        }
        String newKey = normalizeTradingSymbol(entry.tradingSymbol());
        if (newKey == null || newKey.isEmpty()) {
            throw new IllegalArgumentException("tradingSymbol must not be blank");
        }

        synchronized (lock) {
            WatchlistEntry existing = byId.get(entry.symbolId());
            WatchlistEntry toStore;
            if (existing != null) {
                // Preserve addedAt and LinkedHashMap position (put on existing key does not reorder).
                toStore = new WatchlistEntry(
                        entry.symbolId(),
                        entry.tradingSymbol(),
                        entry.exchange(),
                        entry.segment(),
                        entry.instrumentType(),
                        entry.displayName(),
                        existing.addedAt(),
                        entry.active(),
                        entry.bootstrapStatus(),
                        entry.bootstrapError()
                );
                String oldKey = normalizeTradingSymbol(existing.tradingSymbol());
                if (oldKey != null && !oldKey.isEmpty() && !oldKey.equals(newKey)) {
                    String indexed = tradingSymbolIndex.get(oldKey);
                    if (entry.symbolId().equals(indexed)) {
                        tradingSymbolIndex.remove(oldKey);
                    }
                }
            } else {
                toStore = entry;
            }
            byId.put(toStore.symbolId(), toStore);
            tradingSymbolIndex.put(newKey, toStore.symbolId());
            return toStore;
        }
    }

    @Override
    public boolean remove(String symbolId) {
        if (symbolId == null) {
            return false;
        }
        synchronized (lock) {
            WatchlistEntry removed = byId.remove(symbolId);
            if (removed == null) {
                return false;
            }
            String key = normalizeTradingSymbol(removed.tradingSymbol());
            if (key != null && symbolId.equals(tradingSymbolIndex.get(key))) {
                tradingSymbolIndex.remove(key);
            }
            return true;
        }
    }

    @Override
    public int countActive() {
        synchronized (lock) {
            int count = 0;
            for (WatchlistEntry e : byId.values()) {
                if (isPublicActive(e)) {
                    count++;
                }
            }
            return count;
        }
    }
}
