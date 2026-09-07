package com.switchpay.dispute.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface DisputeRepository extends JpaRepository<DisputeEntity, UUID> {
    List<DisputeEntity> findByStateAndEvidenceDueAtBefore(String state, Instant cutoff);
}
