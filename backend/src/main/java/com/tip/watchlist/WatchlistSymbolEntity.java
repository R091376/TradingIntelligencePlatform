package com.tip.watchlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * JPA mapping for {@code watchlist_symbols} (Flyway-owned schema).
 */
@Entity
@Table(name = "watchlist_symbols")
class WatchlistSymbolEntity {

    @Id
    @Column(name = "symbol_id", nullable = false)
    private String symbolId;

    @Column(name = "trading_symbol", nullable = false)
    private String tradingSymbol;

    @Column(name = "exchange", nullable = false)
    private String exchange;

    @Column(name = "segment", nullable = false)
    private String segment;

    @Column(name = "instrument_type", nullable = false)
    private String instrumentType;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "bootstrap_status", nullable = false)
    private SymbolBootstrapStatus bootstrapStatus;

    @Column(name = "bootstrap_error")
    private String bootstrapError;

    protected WatchlistSymbolEntity() {
        // JPA
    }

    static WatchlistSymbolEntity fromDomain(WatchlistEntry entry) {
        WatchlistSymbolEntity e = new WatchlistSymbolEntity();
        e.symbolId = entry.symbolId();
        e.applyMutableFields(entry);
        e.addedAt = entry.addedAt() != null ? entry.addedAt() : Instant.now();
        e.removedAt = null;
        e.active = entry.active();
        return e;
    }

    /**
     * Update mutable columns. When {@code preserveAddedAt} is true (active row update),
     * insertion-order timestamp is left unchanged.
     */
    void applyFromDomain(WatchlistEntry entry, boolean preserveAddedAt) {
        applyMutableFields(entry);
        if (!preserveAddedAt) {
            addedAt = entry.addedAt() != null ? entry.addedAt() : Instant.now();
        }
        active = entry.active();
        if (active) {
            removedAt = null;
        }
    }

    void softDelete(Instant when) {
        active = false;
        removedAt = when;
    }

    private void applyMutableFields(WatchlistEntry entry) {
        tradingSymbol = entry.tradingSymbol();
        exchange = entry.exchange();
        segment = entry.segment();
        instrumentType = entry.instrumentType();
        displayName = entry.displayName();
        bootstrapStatus = entry.bootstrapStatus() != null
                ? entry.bootstrapStatus()
                : SymbolBootstrapStatus.PENDING;
        bootstrapError = entry.bootstrapError();
    }

    WatchlistEntry toDomain() {
        return new WatchlistEntry(
                symbolId,
                tradingSymbol,
                exchange,
                segment,
                instrumentType,
                displayName,
                addedAt,
                active,
                bootstrapStatus,
                bootstrapError
        );
    }

    boolean isActive() {
        return active;
    }
}
