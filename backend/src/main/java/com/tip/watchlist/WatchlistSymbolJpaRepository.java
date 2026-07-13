package com.tip.watchlist;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface WatchlistSymbolJpaRepository extends JpaRepository<WatchlistSymbolEntity, String> {

    List<WatchlistSymbolEntity> findByActiveTrueAndBootstrapStatusNotOrderByAddedAtAsc(
            SymbolBootstrapStatus excludedStatus
    );

    Optional<WatchlistSymbolEntity> findFirstByActiveTrueAndBootstrapStatusNotOrderByAddedAtAsc(
            SymbolBootstrapStatus excludedStatus
    );

    int countByActiveTrueAndBootstrapStatusNot(SymbolBootstrapStatus excludedStatus);

    boolean existsBySymbolIdAndActiveTrueAndBootstrapStatusNot(
            String symbolId,
            SymbolBootstrapStatus excludedStatus
    );

    @Query("""
            SELECT e FROM WatchlistSymbolEntity e
            WHERE e.active = true
              AND e.bootstrapStatus <> :excludedStatus
              AND LOWER(e.tradingSymbol) = LOWER(:tradingSymbol)
            """)
    Optional<WatchlistSymbolEntity> findPublicActiveByTradingSymbolIgnoreCase(
            @Param("tradingSymbol") String tradingSymbol,
            @Param("excludedStatus") SymbolBootstrapStatus excludedStatus
    );
}
