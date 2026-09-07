package com.switchpay.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecordEntity, IdempotencyRecordId> {

    Optional<IdempotencyRecordEntity> findByMerchantIdAndIdempotencyKey(UUID merchantId, String idempotencyKey);

    @Modifying
    @Query(value = """
            DELETE FROM idempotency_record
            WHERE state = 'IN_PROGRESS' AND created_at < :timeoutThreshold
            """, nativeQuery = true)
    int deleteStaleInProgressRecords(@Param("timeoutThreshold") OffsetDateTime timeoutThreshold);

    @Modifying
    @Query(value = """
            DELETE FROM idempotency_record
            WHERE merchant_id = :merchantId AND idempotency_key = :idempotencyKey AND expires_at < :now
            """, nativeQuery = true)
    int deleteExpired(@Param("merchantId") UUID merchantId, @Param("idempotencyKey") String idempotencyKey, @Param("now") OffsetDateTime now);

    @Modifying
    @Query(value = """
            INSERT INTO idempotency_record (merchant_id, idempotency_key, request_fingerprint, state, created_at, expires_at)
            VALUES (:merchantId, :key, :fingerprint, 'IN_PROGRESS', :createdAt, :expiresAt)
            ON CONFLICT (merchant_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertOnConflictDoNothing(
            @Param("merchantId") UUID merchantId,
            @Param("key") String key,
            @Param("fingerprint") byte[] fingerprint,
            @Param("createdAt") OffsetDateTime createdAt,
            @Param("expiresAt") OffsetDateTime expiresAt
    );
}
