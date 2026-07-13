package com.tip.journal.repo;

import com.tip.journal.entity.PatternEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatternEventJpaRepository extends JpaRepository<PatternEventEntity, Long> {

    boolean existsByPatternInstanceIdAndEventType(UUID patternInstanceId, String eventType);
}
