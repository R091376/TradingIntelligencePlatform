package com.tip.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CashLedgerRepository extends JpaRepository<CashLedgerEntity, UUID> {

    List<CashLedgerEntity> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);
}
