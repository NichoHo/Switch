package com.switchpay.payment.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Read-only by construction: {@code payment_event} is append-only (§5.2). */
@Repository
public interface PaymentEventRepository extends JpaRepository<PaymentEventEntity, Long> {
    List<PaymentEventEntity> findByPaymentIdOrderBySeqAsc(UUID paymentId);
}
