package com.switchpay.outbox;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@WireMockTest(httpPort = 8089)
public class WebhookDeliveryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.task.scheduling.enabled", () -> "false"); // Disable scheduled poller so we can run manually
    }

    @Autowired
    private OutboxPollerJob outboxPollerJob;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID merchantId;

    @BeforeEach
    void setup() {
        outboxRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM merchant");
        
        merchantId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO merchant (id, name, api_key_hash, webhook_secret, webhook_url) VALUES (?, 'Test', 'hash', 'secret', 'http://localhost:8089/webhook')", merchantId);
    }

    @Test
    void shouldDeliverWebhookSuccessfully() {
        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(200)));

        OutboxEventEntity event = new OutboxEventEntity(UUID.randomUUID(), merchantId, "test.event", "{}", "PENDING", 0, Instant.now(), Instant.now());
        outboxRepository.save(event);

        outboxPollerJob.processOutboxEvents();

        OutboxEventEntity updated = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo("DELIVERED");

        verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }

    @Test
    void shouldRetryOnFailureWithBackoff() {
        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(503)));

        OutboxEventEntity event = new OutboxEventEntity(UUID.randomUUID(), merchantId, "test.event", "{}", "PENDING", 0, Instant.now(), Instant.now());
        outboxRepository.save(event);

        outboxPollerJob.processOutboxEvents();

        OutboxEventEntity updated = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo("PENDING");
        assertThat(updated.getAttempts()).isEqualTo(1);
        assertThat(updated.getNextAttemptAt()).isAfter(Instant.now());

        verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }
    
    @Test
    void shouldMarkDeadAfterMaxRetries() {
        stubFor(post(urlEqualTo("/webhook"))
                .willReturn(aResponse().withStatus(500)));

        OutboxEventEntity event = new OutboxEventEntity(UUID.randomUUID(), merchantId, "test.event", "{}", "PENDING", 11, Instant.now(), Instant.now());
        outboxRepository.save(event);

        outboxPollerJob.processOutboxEvents();

        OutboxEventEntity updated = outboxRepository.findById(event.getId()).orElseThrow();
        assertThat(updated.getState()).isEqualTo("DEAD");
        assertThat(updated.getAttempts()).isEqualTo(12);

        verify(1, postRequestedFor(urlEqualTo("/webhook")));
    }
}
