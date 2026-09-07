package com.switchpay.routing;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.switchpay.contracts.AuthorizationRequest;
import com.switchpay.contracts.AuthorizationStatusResponse;
import com.switchpay.payment.domain.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@WireMockTest
public class AcquirerFailureMatrixTest {

    private AcquirerClient acquirerClient;
    private String simUrl;
    private AuthorizationRequest req;

    private static AcquirerDirectory.AcquirerEntry entry(String id, int priority, String baseUrl) {
        return new AcquirerDirectory.AcquirerEntry(
                id, Set.of("*"), Set.of("*"), Set.of("*"), priority, 1, true, baseUrl, 0);
    }

    private static AuthorizationRequest makeReq() {
        return new AuthorizationRequest(
                UUID.randomUUID().toString(), "42424242", "4242", "VISA",
                "CREDIT", "GB", "EUR", 1000L,
                UUID.randomUUID().toString(), "ref-" + UUID.randomUUID());
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        this.simUrl = wmRuntimeInfo.getHttpBaseUrl();
        this.acquirerClient = new AcquirerClient(RestClient.builder());
        this.req = makeReq();
    }

    // 1. Connect timeout → SAFE → failover, AUTHORIZED via second acquirer
    @Test
    void connectTimeout_failsOver() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, "http://10.255.255.1:81"),      // unreachable → connect timeout
                entry("A2", 2, simUrl + "/a2")
        ));
        Router r = new Router(dir, acquirerClient);

        stubFor(post(urlEqualTo("/a2/authorizations"))
                .willReturn(okJson("{\"status\":\"APPROVED\",\"acquirerReference\":\"ref1\",\"authCode\":\"123456\",\"declineReason\":null}")));

        Router.RoutingResult result = r.route(req, "VISA", "EUR", "FR");
        assertThat(result.acquirerId()).isEqualTo("A2");
        assertThat(result.resultState()).isEqualTo(PaymentState.AUTHORIZED);
    }

    // 2. Connection refused → SAFE → failover
    @Test
    void connectionRefused_failsOver() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, "http://localhost:54321"),       // refused
                entry("A2", 2, simUrl + "/a2")
        ));
        Router r = new Router(dir, acquirerClient);

        stubFor(post(urlEqualTo("/a2/authorizations"))
                .willReturn(okJson("{\"status\":\"APPROVED\",\"acquirerReference\":\"ref1\",\"authCode\":\"123456\",\"declineReason\":null}")));

        Router.RoutingResult result = r.route(req, "VISA", "EUR", "FR");
        assertThat(result.acquirerId()).isEqualTo("A2");
        assertThat(result.resultState()).isEqualTo(PaymentState.AUTHORIZED);
    }

    // 3. HTTP 503 → SAFE → failover
    @Test
    void http503_failsOver() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, simUrl + "/a1"),
                entry("A2", 2, simUrl + "/a2")
        ));
        Router r = new Router(dir, acquirerClient);

        stubFor(post(urlEqualTo("/a1/authorizations")).willReturn(aResponse().withStatus(503)));
        stubFor(post(urlEqualTo("/a2/authorizations"))
                .willReturn(okJson("{\"status\":\"APPROVED\",\"acquirerReference\":\"ref1\",\"authCode\":\"123456\",\"declineReason\":null}")));

        Router.RoutingResult result = r.route(req, "VISA", "EUR", "FR");
        assertThat(result.acquirerId()).isEqualTo("A2");
        assertThat(result.resultState()).isEqualTo(PaymentState.AUTHORIZED);
    }

    // 4. Read timeout → UNSAFE → AUTH_UNKNOWN, no retry
    @Test
    void readTimeout_authUnknown() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, simUrl + "/a1"),
                entry("A2", 2, simUrl + "/a2")    // must NOT be tried
        ));
        Router r = new Router(dir, acquirerClient);

        stubFor(post(urlEqualTo("/a1/authorizations"))
                .willReturn(aResponse().withFixedDelay(6000).withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"status\":\"APPROVED\"}")));

        Router.RoutingResult result = r.route(req, "VISA", "EUR", "FR");
        assertThat(result.acquirerId()).isEqualTo("A1");
        assertThat(result.resultState()).isEqualTo(PaymentState.AUTH_UNKNOWN);
        // A2 never contacted: verify no request to /a2
        verify(0, postRequestedFor(urlEqualTo("/a2/authorizations")));
    }

    // 5. Read timeout then probe says approved
    @Test
    void probeApproved() {
        stubFor(get(urlPathEqualTo("/acq/authorizations"))
                .withQueryParam("idempotencyKey", equalTo("key1"))
                .willReturn(okJson("{\"status\":\"APPROVED\",\"acquirerReference\":\"ref1\",\"authCode\":\"123456\",\"declineReason\":null}")));

        AuthorizationStatusResponse res = acquirerClient.statusProbe(simUrl + "/acq", "key1");
        assertThat(res.status()).isEqualTo("APPROVED");
    }

    // 6. Read timeout then probe says not found → safe to re-authorize
    @Test
    void probeNotFound() {
        stubFor(get(urlPathEqualTo("/acq/authorizations"))
                .withQueryParam("idempotencyKey", equalTo("key2"))
                .willReturn(okJson("{\"status\":\"NOT_FOUND\",\"acquirerReference\":null,\"authCode\":null,\"declineReason\":null}")));

        AuthorizationStatusResponse res = acquirerClient.statusProbe(simUrl + "/acq", "key2");
        assertThat(res.status()).isEqualTo("NOT_FOUND");
    }

    // 7. Malformed JSON → treated as UNSAFE → AUTH_UNKNOWN
    @Test
    void malformedJson_authUnknown() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, simUrl + "/a1")
        ));
        Router r = new Router(dir, acquirerClient);

        stubFor(post(urlEqualTo("/a1/authorizations"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{malformed")));

        Router.RoutingResult result = r.route(req, "VISA", "EUR", "FR");
        assertThat(result.resultState()).isEqualTo(PaymentState.AUTH_UNKNOWN);
    }

    // 8. Decline → no failover, terminal AUTH_DECLINED
    @Test
    void decline_noFailover() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, simUrl + "/a1"),
                entry("A2", 2, simUrl + "/a2")
        ));
        Router r = new Router(dir, acquirerClient);

        stubFor(post(urlEqualTo("/a1/authorizations"))
                .willReturn(okJson("{\"status\":\"DECLINED\",\"acquirerReference\":\"ref1\",\"authCode\":null,\"declineReason\":\"insufficient_funds\"}")));

        Router.RoutingResult result = r.route(req, "VISA", "EUR", "FR");
        assertThat(result.resultState()).isEqualTo(PaymentState.AUTH_DECLINED);
        assertThat(result.acquirerId()).isEqualTo("A1");
        verify(0, postRequestedFor(urlEqualTo("/a2/authorizations")));
    }

    // 9. All breakers open → NoAcquirerAvailableException, fast
    @Test
    void allBreakersOpen_noAcquirerAvailable() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, simUrl + "/a1")
        ));
        Router r = new Router(dir, acquirerClient);

        // Trip the breaker: need 20 consecutive 503 failures (SAFE, won't stop routing)
        stubFor(post(urlEqualTo("/a1/authorizations")).willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 20; i++) {
            try { r.route(makeReq(), "VISA", "EUR", "FR"); } catch (NoAcquirerAvailableException ignored) {}
        }

        // Now breaker should be OPEN → next call skips the acquirer entirely
        assertThrows(NoAcquirerAvailableException.class, () -> r.route(makeReq(), "VISA", "EUR", "FR"));
    }

    // 10. 20 consecutive failures → breaker opens; subsequent calls skip that acquirer
    @Test
    void breakerOpens_afterConsecutiveFailures() {
        AcquirerDirectory dir = new AcquirerDirectory(List.of(
                entry("A1", 1, simUrl + "/a1"),
                entry("A2", 2, simUrl + "/a2")
        ));
        Router r = new Router(dir, acquirerClient);

        stubFor(post(urlEqualTo("/a1/authorizations")).willReturn(aResponse().withStatus(503)));
        stubFor(post(urlEqualTo("/a2/authorizations"))
                .willReturn(okJson("{\"status\":\"APPROVED\",\"acquirerReference\":\"ref1\",\"authCode\":\"123456\",\"declineReason\":null}")));

        // 20 calls all fail A1, fall back to A2
        for (int i = 0; i < 20; i++) {
            Router.RoutingResult res = r.route(makeReq(), "VISA", "EUR", "FR");
            assertThat(res.acquirerId()).isEqualTo("A2");
        }

        // Reset WireMock to count subsequent requests
        removeAllMappings();
        stubFor(post(urlEqualTo("/a1/authorizations"))
                .willReturn(okJson("{\"status\":\"APPROVED\",\"acquirerReference\":\"ref1\",\"authCode\":\"123456\",\"declineReason\":null}")));
        stubFor(post(urlEqualTo("/a2/authorizations"))
                .willReturn(okJson("{\"status\":\"APPROVED\",\"acquirerReference\":\"ref1\",\"authCode\":\"123456\",\"declineReason\":null}")));

        // Next call should skip A1 (breaker open) and go directly to A2
        Router.RoutingResult result = r.route(makeReq(), "VISA", "EUR", "FR");
        assertThat(result.acquirerId()).isEqualTo("A2");
        verify(0, postRequestedFor(urlEqualTo("/a1/authorizations")));
    }
}
