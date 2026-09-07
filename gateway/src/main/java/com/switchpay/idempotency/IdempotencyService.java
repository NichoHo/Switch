package com.switchpay.idempotency;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {
    private final IdempotencyRecordRepository repository;

    public IdempotencyService(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    public sealed interface ClaimResult {
        record Claimed() implements ClaimResult {}
        record AlreadyCompleted(Integer responseStatus, String responseBody) implements ClaimResult {}
        record InProgress() implements ClaimResult {}
        record KeyReuse() implements ClaimResult {}
    }

    @Transactional
    public ClaimResult claimKey(UUID merchantId, String key, byte[] fingerprint) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusHours(24);

        // Delete expired record if present (so the key can be reused)
        repository.deleteExpired(merchantId, key, now);
        repository.flush();

        // Attempt INSERT ... ON CONFLICT DO NOTHING
        int inserted = repository.insertOnConflictDoNothing(merchantId, key, fingerprint, now, expiresAt);

        if (inserted == 1) {
            return new ClaimResult.Claimed();
        }

        // Conflict — someone else holds this key. Fetch the existing record.
        IdempotencyRecordEntity existing = repository.findByMerchantIdAndIdempotencyKey(merchantId, key)
                .orElseThrow(() -> new IllegalStateException("Conflict but no record found"));

        if (!Arrays.equals(existing.getRequestFingerprint(), fingerprint)) {
            return new ClaimResult.KeyReuse();
        }

        if ("COMPLETED".equals(existing.getState())) {
            return new ClaimResult.AlreadyCompleted(existing.getResponseStatus(), existing.getResponseBody());
        }

        return new ClaimResult.InProgress();
    }

    @Transactional
    public void completeKey(UUID merchantId, String key, Integer responseStatus, String responseBody, UUID paymentId) {
        repository.findByMerchantIdAndIdempotencyKey(merchantId, key).ifPresent(entity -> {
            entity.setState("COMPLETED");
            entity.setResponseStatus(responseStatus);
            entity.setResponseBody(responseBody);
            entity.setPaymentId(paymentId);
            repository.save(entity);
        });
    }

    @Transactional
    public void releaseKey(UUID merchantId, String key) {
        repository.findByMerchantIdAndIdempotencyKey(merchantId, key).ifPresent(repository::delete);
    }
}
