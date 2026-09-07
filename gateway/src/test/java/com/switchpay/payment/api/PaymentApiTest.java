package com.switchpay.payment.api;

import com.jayway.jsonpath.JsonPath;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.ledger.TrialBalanceService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * §16 over real HTTP, against real Postgres. This is the layer that did not exist before:
 * every assertion here was previously unreachable because there were no payment endpoints.
 *
 * Uses MockMvc rather than a live port: the servlet stack, JSON binding, status codes and the
 * §17 problem-detail handler are all exercised, without needing a socket.
 *
 * Every request authenticates with a real {@code Authorization: Bearer} key hashed the same way
 * {@code MerchantAuthFilter} hashes it (NR-4), not an asserted {@code X-Merchant-Id}, which
 * nothing here trusts any more.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public class PaymentApiTest {

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
    private TrialBalanceService trialBalanceService;

    private UUID merchantId;
    private String rawApiKey;

    @BeforeEach
    void setUp() {
        merchantId = insertMerchant();
        rawApiKey = apiKeyFor(merchantId);
        when(acquirerClient.authorize(anyString(), any(), anyString())).thenReturn(
                new AcquirerClient.AcquirerCallResult(RetrySafety.SAFE,
                        new AuthorizationResponse("APPROVED", "VN-8891234", "A19FZ2", null), null));
    }

    private UUID insertMerchant() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO merchant (id, name, api_key_hash, webhook_secret) VALUES (?, 'Test', ?, 'secret')",
                id, ApiKeyHasher.hash(apiKeyFor(id)));
        return id;
    }

    /** Deterministic from the merchant id so a test can compute the same key it inserted. */
    private String apiKeyFor(UUID merchantId) {
        return "sk_test_" + merchantId;
    }

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + rawApiKey);
    }

    private String tokenize(String pan) throws Exception {
        String response = mockMvc.perform(authed(post("/v1/tokens"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pan\":\"" + pan + "\",\"expMonth\":12,\"expYear\":2030}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.brand").value("VISA"))
                .andExpect(jsonPath("$.last4").value(pan.substring(pan.length() - 4)))
                .andReturn().getResponse().getContentAsString();

        assertThat(response)
                .as("a tokenize response must never echo the PAN back")
                .doesNotContain(pan);
        return JsonPath.read(response, "$.token");
    }

    private String createPayment(String token, long minor) throws Exception {
        String response = mockMvc.perform(authed(post("/v1/payments"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merchantReference":"order-%s","cardToken":"%s",
                                 "amount":{"minor":%d,"currency":"EUR"},"capture":false}
                                """.formatted(UUID.randomUUID(), token, minor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("AUTHORIZED"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void tokenize_then_authorize_then_capture_then_refund() throws Exception {
        String token = tokenize("4242424242424242");
        String paymentId = createPayment(token, 5_000);

        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/captures"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"minor\":3000,\"currency\":\"EUR\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PARTIALLY_CAPTURED"))
                .andExpect(jsonPath("$.capturedAmount.minor").value(3000));

        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/refunds"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"minor\":1000,\"currency\":\"EUR\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PARTIALLY_REFUNDED"))
                .andExpect(jsonPath("$.refundedAmount.minor").value(1000));

        assertThat(trialBalanceService.getTrialBalance("EUR")).isZero();
    }

    @Test
    void get_returns_amounts_acquirer_and_card_metadata() throws Exception {
        String paymentId = createPayment(tokenize("4242424242424242"), 4_999);

        mockMvc.perform(authed(get("/v1/payments/" + paymentId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount.minor").value(4999))
                .andExpect(jsonPath("$.amount.currency").value("EUR"))
                .andExpect(jsonPath("$.acquirer.id").value("VISA-NET-EU"))
                .andExpect(jsonPath("$.acquirer.reference").value("VN-8891234"))
                .andExpect(jsonPath("$.authCode").value("A19FZ2"))
                .andExpect(jsonPath("$.risk.decision").value("ALLOW"))
                .andExpect(jsonPath("$.card.brand").value("VISA"))
                .andExpect(jsonPath("$.card.last4").value("4242"))
                .andExpect(jsonPath("$.card.issuerCountry").value("GB"));
    }

    @Test
    void events_endpoint_returns_the_append_only_trail_in_order() throws Exception {
        String paymentId = createPayment(tokenize("4242424242424242"), 5_000);
        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/captures"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"minor\":5000,\"currency\":\"EUR\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/v1/payments/" + paymentId + "/events")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].seq").value(1))
                .andExpect(jsonPath("$[0].toState").value("AUTHORIZED"))
                .andExpect(jsonPath("$[1].seq").value(2))
                .andExpect(jsonPath("$[1].fromState").value("AUTHORIZED"))
                .andExpect(jsonPath("$[1].toState").value("CAPTURED"));
    }

    @Test
    void list_filters_by_state_and_is_scoped_to_the_merchant() throws Exception {
        createPayment(tokenize("4242424242424242"), 1_000);

        mockMvc.perform(authed(get("/v1/payments")).param("state", "AUTHORIZED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(authed(get("/v1/payments")).param("state", "REFUNDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        UUID otherMerchantId = insertMerchant();
        mockMvc.perform(get("/v1/payments")
                        .header("Authorization", "Bearer " + apiKeyFor(otherMerchantId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void a_payment_is_invisible_to_a_merchant_that_does_not_own_it() throws Exception {
        String paymentId = createPayment(tokenize("4242424242424242"), 2_500);

        UUID otherMerchantId = insertMerchant();
        mockMvc.perform(get("/v1/payments/" + paymentId)
                        .header("Authorization", "Bearer " + apiKeyFor(otherMerchantId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_not_found"));
    }

    @Test
    void a_request_with_no_bearer_key_is_401() throws Exception {
        mockMvc.perform(get("/v1/payments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void a_request_with_an_unknown_bearer_key_is_401() throws Exception {
        mockMvc.perform(get("/v1/payments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer sk_test_does_not_exist"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    // --- §17 error model: the codes are the contract ---------------------------------------

    @Test
    void void_after_capture_is_409_void_after_capture() throws Exception {
        String paymentId = createPayment(tokenize("4242424242424242"), 5_000);
        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/captures"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"minor\":5000,\"currency\":\"EUR\"}}"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/voids"))
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_state_invalid"));
    }

    @Test
    void capturing_more_than_authorized_is_422_capture_exceeds_authorized() throws Exception {
        String paymentId = createPayment(tokenize("4242424242424242"), 5_000);

        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/captures"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"minor\":6000,\"currency\":\"EUR\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("capture_exceeds_authorized"));
    }

    @Test
    void refunding_before_capture_is_409_payment_state_invalid() throws Exception {
        String paymentId = createPayment(tokenize("4242424242424242"), 5_000);

        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/refunds"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"minor\":1000,\"currency\":\"EUR\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("payment_state_invalid"));
    }

    @Test
    void capturing_in_another_currency_is_422_currency_mismatch() throws Exception {
        String paymentId = createPayment(tokenize("4242424242424242"), 5_000);

        mockMvc.perform(authed(post("/v1/payments/" + paymentId + "/captures"))
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"minor\":5000,\"currency\":\"USD\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("currency_mismatch"));
    }

    @Test
    void a_real_looking_card_is_422_pan_not_a_test_card() throws Exception {
        // Luhn-valid, but not a documented test BIN (§8.4).
        mockMvc.perform(authed(post("/v1/tokens"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pan\":\"4916338506082832\",\"expMonth\":12,\"expYear\":2030}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("pan_not_a_test_card"));
    }

    @Test
    void an_unknown_payment_is_404() throws Exception {
        mockMvc.perform(authed(get("/v1/payments/" + UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("payment_not_found"));
    }
}
