package com.switchpay.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementItemRepository extends JpaRepository<SettlementItemEntity, UUID> {
    List<SettlementItemEntity> findByBatchId(UUID batchId);
}
