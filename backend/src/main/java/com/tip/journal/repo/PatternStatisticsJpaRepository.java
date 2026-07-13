package com.tip.journal.repo;

import com.tip.journal.entity.PatternStatisticsEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatternStatisticsJpaRepository
        extends JpaRepository<PatternStatisticsEntity, PatternStatisticsEntity.Pk> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM PatternStatisticsEntity s
            WHERE s.symbolId = :symbolId
              AND s.patternType = :patternType
              AND s.timeframe = :timeframe
            """)
    Optional<PatternStatisticsEntity> findForUpdate(
            @Param("symbolId") String symbolId,
            @Param("patternType") String patternType,
            @Param("timeframe") String timeframe
    );

    Optional<PatternStatisticsEntity> findBySymbolIdAndPatternTypeAndTimeframe(
            String symbolId, String patternType, String timeframe
    );
}
