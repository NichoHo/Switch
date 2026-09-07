package com.switchpay.payment.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ThreedsChallengeRepository extends JpaRepository<ThreedsChallengeEntity, UUID> {
    Optional<ThreedsChallengeEntity> findFirstByPaymentIdOrderByCreatedAtDesc(UUID paymentId);
}
