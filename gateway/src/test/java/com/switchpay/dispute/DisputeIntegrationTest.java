package com.switchpay.dispute;

import com.switchpay.common.Currency;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.dispute.store.DisputeRepository;
import com.switchpay.ledger.TrialBalanceService;
import com.switchpay.payment.PaymentService;
import com.switchpay.payment.domain.Payment;
import com.switchpay.payment.store.PaymentRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** §14 — chargeback ledger postings and the payment's denormalised dispute_state. */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class DisputeIntegrationTest {

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
    private DisputeService disputeService;
    @Autowired
    private DisputeRepository disputeRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private TrialBalanceService trialBalanceService;
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

    private UUID captureOnePayment() {
        UUID merchantId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO merchant (id, name, api_key_hash, webhook_secret) VALUES (?, 'Test', 'hash', 'secret')", merchantId);
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        // Unique per call, not a literal shared across every payment this method creates —
        // VELOCITY_CARD_1H is real (NR-10); see its landmine note.
        byte[] panFingerprint = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("""
            INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint,
                                     bin, last4, brand, funding_type, issuer_country, exp_month, exp_year)
            VALUES (?, ?, decode('00', 'hex'), 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'US', 12, 2030)
            """, token, merchantId, panFingerprint);
        Payment payment = paymentService.createPayment(merchantId, "ref-" + UUID.randomUUID(), token, Currency.EUR, 5_000);
        paymentService.authorize(payment.id());
        paymentService.capture(payment.id(), 5_000);
        return payment.id();
    }

    @Test
    void open_then_lose_moves_funds_out_and_flags_the_payment() {
        UUID paymentId = captureOnePayment();

        UUID disputeId = disputeService.open(paymentId, "fraud", 5_000, "EUR");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getDisputeState()).isEqualTo("OPENED");

        disputeService.submitEvidence(disputeId);
        disputeService.resolve(disputeId, false);

        assertThat(disputeRepository.findById(disputeId).orElseThrow().getState()).isEqualTo("LOST");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getDisputeState()).isEqualTo("LOST");
        assertThat(trialBalanceService.getTrialBalance("EUR")).isZero();
    }

    @Test
    void open_then_win_reverses_the_reserve() {
        UUID paymentId = captureOnePayment();

        UUID disputeId = disputeService.open(paymentId, "fraud", 5_000, "EUR");
        disputeService.submitEvidence(disputeId);
        disputeService.resolve(disputeId, true);

        assertThat(disputeRepository.findById(disputeId).orElseThrow().getState()).isEqualTo("WON");
        assertThat(paymentRepository.findById(paymentId).orElseThrow().getDisputeState()).isEqualTo("WON");
        assertThat(trialBalanceService.getTrialBalance("EUR")).isZero();
    }

    @Test
    void expiry_with_no_evidence_goes_straight_to_lost() {
        UUID paymentId = captureOnePayment();

        UUID disputeId = disputeService.open(paymentId, "fraud", 5_000, "EUR");
        disputeService.expire(disputeId);

        assertThat(disputeRepository.findById(disputeId).orElseThrow().getState()).isEqualTo("LOST");
        assertThat(trialBalanceService.getTrialBalance("EUR")).isZero();
    }
}
