package com.switchpay.payment.store;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentEntity p WHERE p.id = :id")
    Optional<PaymentEntity> findAndLockById(@Param("id") UUID id);

    @Query("SELECT p FROM PaymentEntity p WHERE p.state = 'AUTH_UNKNOWN' AND p.createdAt > :cutoff")
    List<PaymentEntity> findAuthUnknown(@Param("cutoff") Instant cutoff);

    List<PaymentEntity> findByMerchantIdOrderByCreatedAtDesc(UUID merchantId);
}
