package com.switchpay.payment.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class PaymentDbConstraintTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("switch")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private EntityManager em;

    private UUID merchantId;
    private String cardToken;

    @BeforeEach
    void insertPrerequisites() {
        merchantId = UUID.randomUUID();
        cardToken = "tok_" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);

        // Insert a merchant (required FK for payment and card_token)
        em.createNativeQuery(
            "INSERT INTO merchant (id, name, api_key_hash, webhook_secret) VALUES (?1, 'Test Merchant', 'hash', 'secret')")
            .setParameter(1, merchantId)
            .executeUpdate();

        // Insert a card_token (required FK for payment)
        em.createNativeQuery(
            "INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint, bin, last4, brand, funding_type, issuer_country, exp_month, exp_year) " +
            "VALUES (?1, ?2, ?3, 1, ?4, '42424242', '4242', 'VISA', 'CREDIT', 'GB', 12, 2030)")
            .setParameter(1, cardToken)
            .setParameter(2, merchantId)
            .setParameter(3, new byte[]{1, 2, 3})
            .setParameter(4, new byte[]{4, 5, 6})
            .executeUpdate();

        em.flush();
    }

    @Test
    void capturedAmount_cannot_exceed_amount() {
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
            "INSERT INTO payment (id, merchant_id, merchant_reference, card_token, amount_minor, currency, state, captured_amount_minor, refunded_amount_minor, version) " +
            "VALUES (?1, ?2, ?3, ?4, 5000, 'USD', 'CREATED', 0, 0, 0)")
            .setParameter(1, id)
            .setParameter(2, merchantId)
            .setParameter(3, "ref-cap-" + UUID.randomUUID())
            .setParameter(4, cardToken)
            .executeUpdate();
        em.flush();

        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery("UPDATE payment SET captured_amount_minor = 6000 WHERE id = ?1")
                    .setParameter(1, id)
                    .executeUpdate();
            em.flush();
        });
    }

    @Test
    void refundedAmount_cannot_exceed_capturedAmount() {
        UUID id = UUID.randomUUID();
        em.createNativeQuery(
            "INSERT INTO payment (id, merchant_id, merchant_reference, card_token, amount_minor, currency, state, captured_amount_minor, refunded_amount_minor, version) " +
            "VALUES (?1, ?2, ?3, ?4, 5000, 'USD', 'CAPTURED', 3000, 0, 0)")
            .setParameter(1, id)
            .setParameter(2, merchantId)
            .setParameter(3, "ref-ref-" + UUID.randomUUID())
            .setParameter(4, cardToken)
            .executeUpdate();
        em.flush();

        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery("UPDATE payment SET refunded_amount_minor = 4000 WHERE id = ?1")
                    .setParameter(1, id)
                    .executeUpdate();
            em.flush();
        });
    }

    @Test
    void unique_merchant_reference() {
        String ref = "ref-unique-" + UUID.randomUUID();
        em.createNativeQuery(
            "INSERT INTO payment (id, merchant_id, merchant_reference, card_token, amount_minor, currency, state, captured_amount_minor, refunded_amount_minor, version) " +
            "VALUES (?1, ?2, ?3, ?4, 5000, 'USD', 'CREATED', 0, 0, 0)")
            .setParameter(1, UUID.randomUUID())
            .setParameter(2, merchantId)
            .setParameter(3, ref)
            .setParameter(4, cardToken)
            .executeUpdate();
        em.flush();

        assertThrows(PersistenceException.class, () -> {
            em.createNativeQuery(
                "INSERT INTO payment (id, merchant_id, merchant_reference, card_token, amount_minor, currency, state, captured_amount_minor, refunded_amount_minor, version) " +
                "VALUES (?1, ?2, ?3, ?4, 5000, 'USD', 'CREATED', 0, 0, 0)")
                .setParameter(1, UUID.randomUUID())
                .setParameter(2, merchantId)
                .setParameter(3, ref)
                .setParameter(4, cardToken)
                .executeUpdate();
            em.flush();
        });
    }
}
