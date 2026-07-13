package com.tip.journal.repo;

import com.tip.journal.entity.PatternOutcomeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PatternOutcomeJpaRepository extends JpaRepository<PatternOutcomeEntity, UUID> {
}
