package com.switchpay.recon.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReconExceptionRepository extends JpaRepository<ReconExceptionEntity, UUID> {
    List<ReconExceptionEntity> findByResolvedAtIsNull();
    long countByTypeAndResolvedAtIsNull(String type);
}
