package com.switchpay.payment.api;

import com.jayway.jsonpath.JsonPath;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.merchant.ApiKeyHasher;
import com.switchpay.routing.AcquirerClient;
import com.switchpay.routing.RetrySafety;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §11 over real HTTP, against real Postgres.
 *
 * The original version of this class predates real Testcontainers wiring anywhere in this
 * package. It mocked {@code PaymentRepository}/{@code ThreedsChallengeRepository} and called
 * {@code controller.callback(challengeId, "SUCCESS")} directly, which only proved that a mock
 * records a call. It could not have caught NR-6 (no signature verification existed to catch) and
 * stopped compiling the moment the controller's signature changed to take a signed body instead
 * of a bare query parameter. This version drives a real CHALLENGE decision through the risk
 * engine (§10), and proves the callback rejects everything §11 step 5 says it must before
 * accepting the one signed, fresh, correctly-bound assertion that completes it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public class ThreedsFlowTest {

    // Matches ThreedsCallbackController's @Value default: no override needed to prove the
    // out-of-the-box demo path actually works end to end.
    private static final String THREEDS_SECRET = "demo-3ds-shared-secret-do-not-use-in-prod";

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
    private MockMvc mockMvc;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String rawApiKey;
    private String cardToken;

    @BeforeEach
    void setUp() {
        // Only reached after a successful 3DS callback drives the payment through the ordinary
        // authorize path (§6.2: AUTHENTICATE_OK returns to CREATED, then AUTHORIZE runs).
        when(acquirerClient.authorize(anyString(), any(), anyString())).thenReturn(
                new AcquirerClient.AcquirerCallResult(RetrySafety.SAFE,
                        new AuthorizationResponse("APPROVED", "VN-1", "A1", null), null));

        UUID merchantId = UUID.randomUUID();
        rawApiKey = "sk_test_" + merchantId;
        // Custom thresholds: 50 = BIN_COUNTRY_MISMATCH(20) + NEW_DEVICE_HIGH_AMOUNT(30) needs a
        // deny/challenge band that actually straddles it; the V1 defaults (80/60) don't.
        jdbcTemplate.update("""
                INSERT INTO merchant (id, name, api_key_hash, webhook_secret, deny_threshold, challenge_threshold)
                VALUES (?, 'Test', ?, 'secret', 70, 40)
                """, merchantId, ApiKeyHasher.hash(rawApiKey));

        cardToken = "tok_" + UUID.randomUUID().toString().replace("-", "");
        // A fingerprint unique per test, not the shared literal other test fixtures in this repo
        // use: VELOCITY_CARD_1H is real now (NR-10), so a fingerprint every test method shared
        // would accumulate history across methods in this class and drift the score this test
        // pins to 50.
        byte[] panFingerprint = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("""
                INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint,
                                         bin, last4, brand, funding_type, issuer_country, exp_month, exp_year)
                VALUES (?, ?, decode('00','hex'), 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'GB', 12, 2030)
                """, cardToken, merchantId, panFingerprint);
    }

    /** GB-issued card, US IP, a device never seen before, and an amount over 50000 minor: score 50. */
    private String createChallengedPayment() throws Exception {
        String response = mockMvc.perform(post("/v1/payments")
                        .header("Authorization", "Bearer " + rawApiKey)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merchantReference":"order-%s","cardToken":"%s",
                                 "amount":{"minor":60000,"currency":"EUR"},"capture":false,
                                 "context":{"ipCountry":"US","deviceFingerprint":"new-device-%s"}}
                                """.formatted(UUID.randomUUID(), cardToken, UUID.randomUUID())))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.state").value("AUTHENTICATION_PENDING"))
                .andExpect(jsonPath("$.risk.score").value(50))
                .andExpect(jsonPath("$.action.type").value("redirect"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private UUID challengeIdFor(String paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT challenge_id FROM threeds_challenge WHERE payment_id = ?",
                UUID.class, UUID.fromString(paymentId));
    }

    private String signedBody(UUID challengeId, String status) {
        return "{\"challengeId\":\"" + challengeId + "\",\"status\":\"" + status + "\"}";
    }

    private String signatureHeader(long timestamp, String body) {
        return "t=" + timestamp + ",v1=" + ThreedsSignature.sign(THREEDS_SECRET, timestamp, body);
    }

    @Test
    void a_signed_success_assertion_authorizes_the_payment() throws Exception {
        String paymentId = createChallengedPayment();
        UUID challengeId = challengeIdFor(paymentId);
        String body = signedBody(challengeId, "SUCCESS");
        long now = Instant.now().getEpochSecond();

        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Switch-Signature", signatureHeader(now, body))
                        .content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/v1/payments/" + paymentId).header("Authorization", "Bearer " + rawApiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("AUTHORIZED"))
                .andExpect(jsonPath("$.liabilityShift").value(true));
    }

    @Test
    void a_signed_failure_assertion_fails_the_authentication() throws Exception {
        String paymentId = createChallengedPayment();
        UUID challengeId = challengeIdFor(paymentId);
        String body = signedBody(challengeId, "FAILURE");
        long now = Instant.now().getEpochSecond();

        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Switch-Signature", signatureHeader(now, body))
                        .content(body))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/v1/payments/" + paymentId).header("Authorization", "Bearer " + rawApiKey))
                .andExpect(jsonPath("$.state").value("AUTHENTICATION_FAILED"));
    }

    @Test
    void a_missing_signature_is_rejected() throws Exception {
        String paymentId = createChallengedPayment();
        UUID challengeId = challengeIdFor(paymentId);

        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signedBody(challengeId, "SUCCESS")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("threeds_signature_missing"));
    }

    @Test
    void a_signature_for_a_different_body_is_rejected() throws Exception {
        String paymentId = createChallengedPayment();
        UUID challengeId = challengeIdFor(paymentId);
        long now = Instant.now().getEpochSecond();
        // Signed over "SUCCESS", sent with "FAILURE": the classic tamper.
        String signature = signatureHeader(now, signedBody(challengeId, "SUCCESS"));

        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Switch-Signature", signature)
                        .content(signedBody(challengeId, "FAILURE")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("threeds_signature_invalid"));
    }

    @Test
    void a_stale_timestamp_is_rejected_even_with_a_correct_signature() throws Exception {
        String paymentId = createChallengedPayment();
        UUID challengeId = challengeIdFor(paymentId);
        String body = signedBody(challengeId, "SUCCESS");
        long tenMinutesAgo = Instant.now().getEpochSecond() - 600;

        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Switch-Signature", signatureHeader(tenMinutesAgo, body))
                        .content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("threeds_signature_expired"));
    }

    @Test
    void a_valid_signature_for_the_wrong_challenge_id_is_rejected() throws Exception {
        String paymentId = createChallengedPayment();
        UUID challengeId = challengeIdFor(paymentId);
        // Correctly signed, but the body claims a challenge the path doesn't name.
        String body = signedBody(UUID.randomUUID(), "SUCCESS");
        long now = Instant.now().getEpochSecond();

        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Switch-Signature", signatureHeader(now, body))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("threeds_challenge_id_mismatch"));
    }

    @Test
    void a_replayed_assertion_against_an_already_resolved_challenge_is_rejected() throws Exception {
        String paymentId = createChallengedPayment();
        UUID challengeId = challengeIdFor(paymentId);
        String body = signedBody(challengeId, "SUCCESS");

        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Switch-Signature", signatureHeader(Instant.now().getEpochSecond(), body))
                        .content(body))
                .andExpect(status().isOk());

        // Same assertion, freshly re-signed: a genuinely valid signature, replayed too late.
        mockMvc.perform(post("/3ds/callback/" + challengeId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Switch-Signature", signatureHeader(Instant.now().getEpochSecond(), body))
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("threeds_challenge_already_resolved"));
    }
}
