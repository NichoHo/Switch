package com.switchpay.payment.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentOperationRepository extends JpaRepository<PaymentOperationEntity, UUID> {

    @Query("""
        SELECT DISTINCT p.merchantId, o.currency, o.acquirerId
        FROM PaymentOperationEntity o JOIN PaymentEntity p ON p.id = o.paymentId
        WHERE o.type = 'CAPTURE' AND o.settlementState = 'UNSETTLED' AND o.createdAt < :before
        """)
    List<Object[]> findUnsettledGroups(@Param("before") Instant before);

    @Query("""
        SELECT o FROM PaymentOperationEntity o JOIN PaymentEntity p ON p.id = o.paymentId
        WHERE o.type = 'CAPTURE' AND o.settlementState = 'UNSETTLED' AND o.createdAt < :before
          AND p.merchantId = :merchantId AND o.currency = :currency AND o.acquirerId = :acquirerId
        """)
    List<PaymentOperationEntity> findUnsettledCaptures(
        @Param("merchantId") UUID merchantId, @Param("currency") String currency,
        @Param("acquirerId") String acquirerId, @Param("before") Instant before);

    @Query("""
        SELECT o FROM PaymentOperationEntity o JOIN PaymentEntity p ON p.id = o.paymentId
        WHERE o.type = 'REFUND' AND o.settlementState = 'UNSETTLED' AND o.createdAt < :before
          AND p.merchantId = :merchantId AND o.currency = :currency AND o.acquirerId = :acquirerId
        """)
    List<PaymentOperationEntity> findUnsettledRefunds(
        @Param("merchantId") UUID merchantId, @Param("currency") String currency,
        @Param("acquirerId") String acquirerId, @Param("before") Instant before);

    @Query("SELECT o FROM PaymentOperationEntity o WHERE o.type = 'CAPTURE' AND o.createdAt >= :start AND o.createdAt < :end")
    List<PaymentOperationEntity> findCapturesInWindow(@Param("start") Instant start, @Param("end") Instant end);
}
