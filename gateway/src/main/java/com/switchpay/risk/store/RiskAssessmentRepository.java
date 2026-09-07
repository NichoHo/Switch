package com.switchpay.risk.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.UUID;

/** Risk rules' view of history: every velocity signal in §10.1 is a query against this table. */
@Repository
public interface RiskAssessmentRepository extends JpaRepository<RiskAssessmentEntity, UUID> {

    @Query("SELECT COUNT(r) FROM RiskAssessmentEntity r WHERE r.panFingerprint = :fingerprint AND r.createdAt > :since")
    long countByPanFingerprintSince(@Param("fingerprint") String fingerprint, @Param("since") Instant since);

    @Query("SELECT COUNT(DISTINCT r.panFingerprint) FROM RiskAssessmentEntity r WHERE r.ipAddress = :ip AND r.createdAt > :since")
    long countDistinctCardsByIpSince(@Param("ip") String ip, @Param("since") Instant since);

    @Query("SELECT COUNT(r) FROM RiskAssessmentEntity r WHERE r.emailHash = :emailHash AND r.createdAt > :since")
    long countByEmailHashSince(@Param("emailHash") String emailHash, @Param("since") Instant since);

    @Query("""
        SELECT COUNT(r) FROM RiskAssessmentEntity r
        WHERE r.ipAddress = :ip AND r.createdAt > :since AND r.amountMinor <= :smallAmountMinor
        """)
    long countSmallAmountsByIpSince(@Param("ip") String ip, @Param("since") Instant since,
                                     @Param("smallAmountMinor") long smallAmountMinor);

    boolean existsByDeviceFingerprint(String deviceFingerprint);
}
