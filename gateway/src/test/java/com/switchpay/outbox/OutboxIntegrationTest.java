package com.switchpay.outbox;

import com.switchpay.payment.PaymentService;
import com.switchpay.payment.domain.Payment;
import com.switchpay.common.Currency;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.routing.AcquirerClient;
import com.switchpay.routing.RetrySafety;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class OutboxIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AcquirerClient acquirerClient;

    @BeforeEach
    void acquirerApproves() {
        when(acquirerClient.authorize(anyString(), any(), anyString())).thenReturn(
                new AcquirerClient.AcquirerCallResult(RetrySafety.SAFE,
                        new AuthorizationResponse("APPROVED", "VN-1", "A1", null), null));
    }

    @Test
    void shouldAtomicallySaveOutboxEvent() {
        UUID merchantId = UUID.randomUUID();
        // Insert dummy merchant to satisfy foreign keys
        jdbcTemplate.update("INSERT INTO merchant (id, name, api_key_hash, webhook_secret, webhook_url) VALUES (?, 'Test', 'hash', 'secret', 'http://localhost')", merchantId);
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        byte[] panFingerprint = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("""
            INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint,
                                     bin, last4, brand, funding_type, issuer_country, exp_month, exp_year)
            VALUES (?, ?, decode('00', 'hex'), 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'US', 12, 2030)
            """, token, merchantId, panFingerprint);

        // create payment (doesn't trigger event directly)
        Payment payment = paymentService.createPayment(merchantId, "ref", token, Currency.USD, 1000);
        
        // authorize triggers state transition and outbox event
        paymentService.authorize(payment.id());
        
        List<OutboxEventEntity> events = outboxRepository.findAll();
        assertThat(events).isNotEmpty();
        
        OutboxEventEntity event = events.get(0);
        assertThat(event.getMerchantId()).isEqualTo(merchantId);
        assertThat(event.getState()).isEqualTo("PENDING");
        assertThat(event.getPayload()).contains(payment.id().toString());
    }
}
