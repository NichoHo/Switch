package com.switchpay.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.merchant.ApiKeyHasher;
import com.switchpay.payment.store.PaymentRepository;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §7 and §19.3 over real HTTP.
 *
 * IdempotencyServiceTest proves the claim/complete/release state machine by calling the service
 * with hand-built fingerprints. That is exactly why it never caught the bug this class exists
 * for: the filter fingerprinted an unread body, so every JSON request hashed identically and
 * "same key, different body" quietly replayed the first response instead of rejecting.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public class IdempotencyFilterTest {

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
    @Autowired
    private PaymentRepository paymentRepository;

    private static final ObjectMapper JSON = new ObjectMapper();

    private UUID merchantId;
    private String rawApiKey;
    private String cardToken;

    /**
     * The stored response comes back out of a {@code jsonb} column, and Postgres re-serialises
     * jsonb with its own key order (by length, then lexically) and its own spacing: a faithful
     * replay is not required to be byte-identical to the original response, only equivalent.
     */
    private void assertSameJson(String actual, String expected) throws Exception {
        assertThat(JSON.readTree(actual)).isEqualTo(JSON.readTree(expected));
    }

    @BeforeEach
    void setUp() {
        merchantId = UUID.randomUUID();
        rawApiKey = apiKeyFor(merchantId);
        jdbcTemplate.update(
                "INSERT INTO merchant (id, name, api_key_hash, webhook_secret) VALUES (?, 'Test', ?, 'secret')",
                merchantId, ApiKeyHasher.hash(rawApiKey));
        cardToken = insertCardToken(merchantId);
        when(acquirerClient.authorize(anyString(), any(), anyString())).thenReturn(
                new AcquirerClient.AcquirerCallResult(RetrySafety.SAFE,
                        new AuthorizationResponse("APPROVED", "VN-1", "A1", null), null));
    }

    private String apiKeyFor(UUID merchantId) {
        return "sk_test_" + merchantId;
    }

    private String insertCardToken(UUID merchantId) {
        String token = "tok_" + UUID.randomUUID().toString().replace("-", "");
        // Unique per call, not a literal shared across every card this class inserts:
        // VELOCITY_CARD_1H is real (NR-10); see its landmine note.
        byte[] panFingerprint = UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        jdbcTemplate.update("""
                INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint,
                                         bin, last4, brand, funding_type, issuer_country, exp_month, exp_year)
                VALUES (?, ?, decode('00','hex'), 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'GB', 12, 2030)
                """, token, merchantId, panFingerprint);
        return token;
    }

    private String paymentBody(String reference, long minor) {
        return paymentBody(reference, minor, cardToken);
    }

    private String paymentBody(String reference, long minor, String token) {
        return """
                {"merchantReference":"%s","cardToken":"%s","amount":{"minor":%d,"currency":"EUR"},"capture":false}
                """.formatted(reference, token, minor);
    }

    private ResultActions postPayment(String key, String body) throws Exception {
        return postPayment(rawApiKey, key, body);
    }

    private ResultActions postPayment(String apiKey, String key, String body) throws Exception {
        return mockMvc.perform(post("/v1/payments")
                .header("Authorization", "Bearer " + apiKey)
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    @Test
    void the_handler_still_receives_the_body_the_filter_read() throws Exception {
        // The regression guard for CachedBodyRequest: if the body were consumed by the
        // fingerprint, binding would fail and this would never reach 201.
        postPayment(UUID.randomUUID().toString(), paymentBody("order-1", 4_999))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount.minor").value(4999));
    }

    @Test
    void same_key_same_body_charges_once_and_replays_the_stored_response() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = paymentBody("order-dup", 5_000);

        String first = postPayment(key, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String second = postPayment(key, body)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertSameJson(second, first);
        assertThat(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)).hasSize(1);
        // The claim that actually matters: the cardholder was authorized once, not twice.
        verify(acquirerClient, times(1)).authorize(anyString(), any(), anyString());
    }

    @Test
    void same_key_different_body_is_422_idempotency_key_reuse() throws Exception {
        String key = UUID.randomUUID().toString();

        postPayment(key, paymentBody("order-a", 5_000)).andExpect(status().isCreated());

        postPayment(key, paymentBody("order-a", 9_999))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("idempotency_key_reuse"));

        assertThat(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId))
                .as("the original payment must be untouched")
                .hasSize(1);
        verify(acquirerClient, times(1)).authorize(anyString(), any(), anyString());
    }

    @Test
    void a_body_differing_only_in_whitespace_and_key_order_is_the_same_request() throws Exception {
        String key = UUID.randomUUID().toString();

        String first = postPayment(key, """
                {"merchantReference":"order-canon","cardToken":"%s","amount":{"minor":5000,"currency":"EUR"},"capture":false}
                """.formatted(cardToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Same request, reformatted: §7 says canonicalisation must see through this.
        String second = postPayment(key, """
                {
                    "capture" : false,
                    "cardToken" : "%s",
                    "amount" : { "currency" : "EUR", "minor" : 5000 },
                    "merchantReference" : "order-canon"
                }
                """.formatted(cardToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(second, "$.id")).isEqualTo(JsonPath.read(first, "$.id"));
        verify(acquirerClient, times(1)).authorize(anyString(), any(), anyString());
    }

    @Test
    void different_keys_are_independent_payments() throws Exception {
        postPayment(UUID.randomUUID().toString(), paymentBody("order-x", 1_000))
                .andExpect(status().isCreated());
        postPayment(UUID.randomUUID().toString(), paymentBody("order-y", 1_000))
                .andExpect(status().isCreated());

        assertThat(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)).hasSize(2);
        verify(acquirerClient, times(2)).authorize(anyString(), any(), anyString());
    }

    @Test
    void the_same_key_belongs_to_each_merchant_separately() throws Exception {
        String key = UUID.randomUUID().toString();
        UUID otherMerchant = UUID.randomUUID();
        String otherApiKey = apiKeyFor(otherMerchant);
        jdbcTemplate.update(
                "INSERT INTO merchant (id, name, api_key_hash, webhook_secret) VALUES (?, 'Other', ?, 'secret')",
                otherMerchant, com.switchpay.merchant.ApiKeyHasher.hash(otherApiKey));
        String otherToken = insertCardToken(otherMerchant);

        postPayment(key, paymentBody("order-shared", 2_000)).andExpect(status().isCreated());
        postPayment(otherApiKey, key, paymentBody("order-shared", 2_000, otherToken))
                .andExpect(status().isCreated());

        assertThat(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(merchantId)).hasSize(1);
        assertThat(paymentRepository.findByMerchantIdOrderByCreatedAtDesc(otherMerchant)).hasSize(1);
    }

    @Test
    void a_client_error_is_stored_and_replayed_rather_than_re_executed() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = paymentBody("order-bad", 5_000).replace(cardToken, "tok_does_not_exist");

        postPayment(key, body).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("card_token_not_found"));

        // 4xx is a real answer about a real request: replaying it is correct, and cheaper
        // than re-running the handler to reach the same conclusion.
        postPayment(key, body).andExpect(status().isNotFound());
    }
}
