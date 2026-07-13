package com.tip.journal.repo;

import com.tip.journal.entity.PatternInstanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface PatternInstanceJpaRepository extends JpaRepository<PatternInstanceEntity, UUID> {

    List<PatternInstanceEntity> findBySymbolIdAndTimeframeAndStatusIn(
            String symbolId, String timeframe, Collection<String> statuses
    );

    List<PatternInstanceEntity> findBySymbolIdAndStatusIn(String symbolId, Collection<String> statuses);

    List<PatternInstanceEntity> findByTimeframeInAndStatusIn(
            Collection<String> timeframes, Collection<String> statuses
    );

    List<PatternInstanceEntity> findBySymbolIdOrderByDetectedAtDesc(String symbolId);

    List<PatternInstanceEntity> findBySymbolIdAndStatusInOrderByDetectedAtDesc(
            String symbolId, Collection<String> statuses
    );
}
