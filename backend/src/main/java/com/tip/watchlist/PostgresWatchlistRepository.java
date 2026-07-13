package com.tip.watchlist;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * PostgreSQL {@link WatchlistRepository}.
 * <p>
 * Active when {@code tip.watchlist.store=postgres} (default). KD27: remove soft-deletes
 * ({@code is_active=false}, {@code removed_at}). Re-add reactivates by instrument key.
 * Primary is {@code added_at ASC}.
 */
@Repository
@ConditionalOnProperty(name = "tip.watchlist.store", havingValue = "postgres", matchIfMissing = true)
public class PostgresWatchlistRepository implements WatchlistRepository {

    private final WatchlistSymbolJpaRepository jpa;

    public PostgresWatchlistRepository(WatchlistSymbolJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    @Transactional(readOnly = true)
    public List<WatchlistEntry> findAllActive() {
        return jpa.findByActiveTrueAndBootstrapStatusNotOrderByAddedAtAsc(SymbolBootstrapStatus.REMOVING)
                .stream()
                .map(WatchlistSymbolEntity::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WatchlistEntry> findPrimary() {
        return jpa.findFirstByActiveTrueAndBootstrapStatusNotOrderByAddedAtAsc(SymbolBootstrapStatus.REMOVING)
                .map(WatchlistSymbolEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WatchlistEntry> findBySymbolId(String symbolId) {
        if (symbolId == null) {
            return Optional.empty();
        }
        // REMOVING stays active=true until soft-delete; soft-deleted rows are hidden.
        return jpa.findById(symbolId)
                .filter(WatchlistSymbolEntity::isActive)
                .map(WatchlistSymbolEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean containsSymbolId(String symbolId) {
        if (symbolId == null) {
            return false;
        }
        return jpa.existsBySymbolIdAndActiveTrueAndBootstrapStatusNot(
                symbolId, SymbolBootstrapStatus.REMOVING);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<WatchlistEntry> findByTradingSymbolIgnoreCase(String tradingSymbol) {
        if (tradingSymbol == null || tradingSymbol.isBlank()) {
            return Optional.empty();
        }
        return jpa.findPublicActiveByTradingSymbolIgnoreCase(
                        tradingSymbol.trim(), SymbolBootstrapStatus.REMOVING)
                .map(WatchlistSymbolEntity::toDomain);
    }

    @Override
    @Transactional
    public WatchlistEntry save(WatchlistEntry entry) {
        Objects.requireNonNull(entry, "entry");
        Objects.requireNonNull(entry.symbolId(), "symbolId");
        Objects.requireNonNull(entry.tradingSymbol(), "tradingSymbol");
        if (entry.symbolId().isBlank()) {
            throw new IllegalArgumentException("symbolId must not be blank");
        }
        if (entry.tradingSymbol().isBlank()) {
            throw new IllegalArgumentException("tradingSymbol must not be blank");
        }

        Optional<WatchlistSymbolEntity> existingOpt = jpa.findById(entry.symbolId());
        if (existingOpt.isPresent()) {
            WatchlistSymbolEntity existing = existingOpt.get();
            // Active update keeps addedAt; soft-deleted reactivates with caller's addedAt.
            existing.applyFromDomain(entry, existing.isActive());
            return jpa.save(existing).toDomain();
        }

        return jpa.save(WatchlistSymbolEntity.fromDomain(entry)).toDomain();
    }

    @Override
    @Transactional
    public boolean remove(String symbolId) {
        if (symbolId == null) {
            return false;
        }
        Optional<WatchlistSymbolEntity> opt = jpa.findById(symbolId);
        if (opt.isEmpty()) {
            return false;
        }
        WatchlistSymbolEntity entity = opt.get();
        if (!entity.isActive()) {
            return true;
        }
        entity.softDelete(Instant.now());
        jpa.save(entity);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public int countActive() {
        return jpa.countByActiveTrueAndBootstrapStatusNot(SymbolBootstrapStatus.REMOVING);
    }
}
