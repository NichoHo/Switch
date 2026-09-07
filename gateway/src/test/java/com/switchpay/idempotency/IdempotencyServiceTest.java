package com.switchpay.idempotency;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers
public class IdempotencyServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("switchpay")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.flyway.url", postgres::getJdbcUrl);
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);
    }

    @Autowired
    private IdempotencyService service;

    @Autowired
    private IdempotencyRecordRepository repository;

    @Autowired
    private StaleKeyReaper reaper;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void testSameKeyTwiceSequentially() {
        UUID merchantId = UUID.randomUUID();
        String key = "key-1";
        byte[] fingerprint = RequestFingerprint.compute("POST", "/v1/payments", "{\"amount\":100}");

        var claim1 = service.claimKey(merchantId, key, fingerprint);
        assertThat(claim1).isInstanceOf(IdempotencyService.ClaimResult.Claimed.class);

        service.completeKey(merchantId, key, 200, "{\"status\":\"ok\"}", UUID.randomUUID());

        var claim2 = service.claimKey(merchantId, key, fingerprint);
        assertThat(claim2).isInstanceOf(IdempotencyService.ClaimResult.AlreadyCompleted.class);
        var completed = (IdempotencyService.ClaimResult.AlreadyCompleted) claim2;
        assertThat(completed.responseStatus()).isEqualTo(200);
        // responseBody is a jsonb column: Postgres re-serialises with its own spacing
        // ("ok" -> " ok"), so the round trip is equivalent JSON, not a byte-identical string.
        assertThat(completed.responseBody()).isEqualToIgnoringWhitespace("{\"status\":\"ok\"}");
    }

    @Test
    void testSameKey20ThreadsSimultaneously() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        UUID merchantId = UUID.randomUUID();
        String key = "key-parallel";
        byte[] fingerprint = RequestFingerprint.compute("POST", "/v1/payments", "{}");

        AtomicInteger claimedCount = new AtomicInteger(0);
        AtomicInteger inProgressCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    var result = service.claimKey(merchantId, key, fingerprint);
                    if (result instanceof IdempotencyService.ClaimResult.Claimed) {
                        claimedCount.incrementAndGet();
                    } else if (result instanceof IdempotencyService.ClaimResult.InProgress) {
                        inProgressCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    // Ignore
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await(5, TimeUnit.SECONDS);

        assertThat(claimedCount.get()).isEqualTo(1);
        assertThat(inProgressCount.get()).isEqualTo(19);
        executor.shutdown();
    }

    @Test
    void testSameKeyDifferentBody() {
        UUID merchantId = UUID.randomUUID();
        String key = "key-diff-body";
        byte[] fingerprintA = RequestFingerprint.compute("POST", "/v1/payments", "{\"amount\":100}");
        byte[] fingerprintB = RequestFingerprint.compute("POST", "/v1/payments", "{\"amount\":200}");

        service.claimKey(merchantId, key, fingerprintA);
        service.completeKey(merchantId, key, 200, "{\"status\":\"ok\"}", UUID.randomUUID());

        var claim2 = service.claimKey(merchantId, key, fingerprintB);
        assertThat(claim2).isInstanceOf(IdempotencyService.ClaimResult.KeyReuse.class);
    }

    @Test
    void testSameKeyDifferentMerchant() {
        UUID merchantId1 = UUID.randomUUID();
        UUID merchantId2 = UUID.randomUUID();
        String key = "key-same";
        byte[] fingerprint = RequestFingerprint.compute("POST", "/v1/payments", "{}");

        var claim1 = service.claimKey(merchantId1, key, fingerprint);
        var claim2 = service.claimKey(merchantId2, key, fingerprint);

        assertThat(claim1).isInstanceOf(IdempotencyService.ClaimResult.Claimed.class);
        assertThat(claim2).isInstanceOf(IdempotencyService.ClaimResult.Claimed.class);
    }

    @Test
    void testKeyExpiry() {
        UUID merchantId = UUID.randomUUID();
        String key = "key-expired";
        byte[] fingerprint = RequestFingerprint.compute("POST", "/v1/payments", "{}");

        // Insert expired record manually
        IdempotencyRecordEntity entity = new IdempotencyRecordEntity();
        entity.setMerchantId(merchantId);
        entity.setIdempotencyKey(key);
        entity.setRequestFingerprint(fingerprint);
        entity.setState("COMPLETED");
        entity.setCreatedAt(OffsetDateTime.now().minusDays(2));
        entity.setExpiresAt(OffsetDateTime.now().minusDays(1)); // Expired
        repository.save(entity);

        // Claiming should treat it as fresh since it's expired
        var claim = service.claimKey(merchantId, key, fingerprint);
        assertThat(claim).isInstanceOf(IdempotencyService.ClaimResult.Claimed.class);
    }

    @Test
    void testHandlerThrowsRelease() {
        UUID merchantId = UUID.randomUUID();
        String key = "key-throws";
        byte[] fingerprint = RequestFingerprint.compute("POST", "/v1/payments", "{}");

        service.claimKey(merchantId, key, fingerprint);
        service.releaseKey(merchantId, key);

        var claim2 = service.claimKey(merchantId, key, fingerprint);
        assertThat(claim2).isInstanceOf(IdempotencyService.ClaimResult.Claimed.class);
    }

    @Test
    void testStaleInProgressReaped() {
        UUID merchantId = UUID.randomUUID();
        String key = "key-stale";
        byte[] fingerprint = RequestFingerprint.compute("POST", "/v1/payments", "{}");

        IdempotencyRecordEntity entity = new IdempotencyRecordEntity();
        entity.setMerchantId(merchantId);
        entity.setIdempotencyKey(key);
        entity.setRequestFingerprint(fingerprint);
        entity.setState("IN_PROGRESS");
        entity.setCreatedAt(OffsetDateTime.now().minusMinutes(10));
        entity.setExpiresAt(OffsetDateTime.now().plusHours(24));
        repository.save(entity);

        reaper.reapStaleKeys();

        var claim = service.claimKey(merchantId, key, fingerprint);
        assertThat(claim).isInstanceOf(IdempotencyService.ClaimResult.Claimed.class);
    }
}
