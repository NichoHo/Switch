package com.switchpay.settlement;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementBatchRepository extends JpaRepository<SettlementBatchEntity, UUID> {
    Optional<SettlementBatchEntity> findByMerchantIdAndCurrencyAndAcquirerIdAndBusinessDate(
        UUID merchantId, String currency, String acquirerId, LocalDate businessDate);

    List<SettlementBatchEntity> findAllByOrderByCreatedAtDesc();
}
