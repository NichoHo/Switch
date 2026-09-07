package com.switchpay.payment;

import com.switchpay.common.Currency;
import com.switchpay.contracts.AuthorizationRequest;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.payment.domain.Payment;
import com.switchpay.payment.store.PaymentEntity;
import com.switchpay.payment.store.PaymentRepository;
import com.switchpay.routing.AcquirerClient;
import com.switchpay.routing.NoAcquirerAvailableException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * §3 steps 6–7: proves the payment flow actually goes through the Router rather than around it.
 *
 * The HTTP boundary ({@link AcquirerClient}) is the only thing mocked — candidate selection,
 * priority ordering, retry-safety classification and failover all run for real. The wire-level
 * behaviour of the client itself is covered by AcquirerFailureMatrixTest.
 *
 * The card is a GB Visa paying in EUR, which makes two acquirers eligible: VISA-NET-EU
 * (priority 10) and FALLBACK-GLOBAL (priority 90). That ordering is what the failover case needs.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class PaymentAuthorizationRoutingTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockBean
    private AcquirerClient acquirerClient;

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID merchantId;
    private String cardToken;

    @BeforeEach
    void seedMerchantAndCard() {
        merchantId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO merchant (id, name, api_key_hash, webhook_secret) VALUES (?, 'Test', 'hash', 'secret')",
                merchantId);
        cardToken = "tok_" + UUID.randomUUID().toString().replace("-", "");
        // A fingerprint unique per test method, not a literal shared by every card_token row in
        // this class — VELOCITY_CARD_1H is real (NR-10), so a shared fingerprint would accumulate
        // score across this class's six test methods (NR-10's own landmine note).
        byte[] panFingerprint = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("""
                INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint,
                                         bin, last4, brand, funding_type, issuer_country, exp_month, exp_year)
                VALUES (?, ?, decode('00','hex'), 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'GB', 12, 2030)
                """, cardToken, merchantId, panFingerprint);
    }

    private AcquirerClient.AcquirerCallResult approved() {
        return new AcquirerClient.AcquirerCallResult(
                RetrySafety.SAFE, new AuthorizationResponse("APPROVED", "VN-8891234", "A19FZ2", null), null);
    }

    private AcquirerClient.AcquirerCallResult declined() {
        return new AcquirerClient.AcquirerCallResult(
                RetrySafety.SAFE, new AuthorizationResponse("DECLINED", "VN-9000001", null, "insufficient_funds"), null);
    }

    private PaymentEntity authorizeOnePayment() {
        Payment payment = paymentService.createPayment(
                merchantId, "ref-" + UUID.randomUUID(), cardToken, Currency.EUR, 10_000);
        paymentService.authorize(payment.id());
        return paymentRepository.findById(payment.id()).orElseThrow();
    }

    @Test
    void approved_by_the_highest_priority_acquirer_persists_its_reference_and_auth_code() {
        when(acquirerClient.authorize(anyString(), any(), eq("VISA-NET-EU"))).thenReturn(approved());

        PaymentEntity entity = authorizeOnePayment();

        assertThat(entity.getState()).isEqualTo("AUTHORIZED");
        assertThat(entity.getAcquirerId()).isEqualTo("VISA-NET-EU");
        assertThat(entity.getAcquirerReference()).isEqualTo("VN-8891234");
        assertThat(entity.getAuthCode()).isEqualTo("A19FZ2");
    }

    @Test
    void the_idempotency_key_sent_to_the_acquirer_is_the_payment_id() {
        when(acquirerClient.authorize(anyString(), any(), eq("VISA-NET-EU"))).thenReturn(approved());

        PaymentEntity entity = authorizeOnePayment();

        // This is the key StatusProbeJob will later ask about — if it were anything else,
        // an AUTH_UNKNOWN could never be resolved.
        var sent = org.mockito.ArgumentCaptor.forClass(AuthorizationRequest.class);
        verify(acquirerClient).authorize(anyString(), sent.capture(), eq("VISA-NET-EU"));
        assertThat(sent.getValue().idempotencyKey()).isEqualTo(entity.getId().toString());
        assertThat(sent.getValue().amountMinor()).isEqualTo(10_000);
        assertThat(sent.getValue().currency()).isEqualTo("EUR");
        assertThat(sent.getValue().brand()).isEqualTo("VISA");
    }

    @Test
    void a_safe_failure_fails_over_to_the_next_candidate() {
        when(acquirerClient.authorize(anyString(), any(), eq("VISA-NET-EU")))
                .thenReturn(new AcquirerClient.AcquirerCallResult(RetrySafety.SAFE, null, "Server unavailable (503)"));
        when(acquirerClient.authorize(anyString(), any(), eq("FALLBACK-GLOBAL"))).thenReturn(approved());

        PaymentEntity entity = authorizeOnePayment();

        assertThat(entity.getState()).isEqualTo("AUTHORIZED");
        assertThat(entity.getAcquirerId()).isEqualTo("FALLBACK-GLOBAL");
    }

    @Test
    void a_read_timeout_parks_the_payment_in_auth_unknown_and_keeps_the_acquirer_attribution() {
        when(acquirerClient.authorize(anyString(), any(), eq("VISA-NET-EU")))
                .thenReturn(new AcquirerClient.AcquirerCallResult(RetrySafety.UNSAFE, null, "Read timeout"));

        PaymentEntity entity = authorizeOnePayment();

        assertThat(entity.getState()).isEqualTo("AUTH_UNKNOWN");
        assertThat(entity.getAcquirerId())
                .as("StatusProbeJob skips any AUTH_UNKNOWN payment with no acquirer id")
                .isEqualTo("VISA-NET-EU");
        // UNSAFE means the request may have landed: the second candidate must never be tried.
        verify(acquirerClient, org.mockito.Mockito.never())
                .authorize(anyString(), any(), eq("FALLBACK-GLOBAL"));
    }

    @Test
    void a_decline_is_terminal_and_never_fails_over() {
        when(acquirerClient.authorize(anyString(), any(), eq("VISA-NET-EU"))).thenReturn(declined());

        PaymentEntity entity = authorizeOnePayment();

        assertThat(entity.getState()).isEqualTo("AUTH_DECLINED");
        assertThat(entity.getAcquirerId()).isEqualTo("VISA-NET-EU");
        verify(acquirerClient, org.mockito.Mockito.never())
                .authorize(anyString(), any(), eq("FALLBACK-GLOBAL"));
    }

    @Test
    void every_candidate_failing_safely_exhausts_the_directory() {
        when(acquirerClient.authorize(anyString(), any(), anyString()))
                .thenReturn(new AcquirerClient.AcquirerCallResult(RetrySafety.SAFE, null, "Connect failure"));

        Payment payment = paymentService.createPayment(
                merchantId, "ref-" + UUID.randomUUID(), cardToken, Currency.EUR, 10_000);

        assertThatThrownBy(() -> paymentService.authorize(payment.id()))
                .isInstanceOf(NoAcquirerAvailableException.class);
    }
}
