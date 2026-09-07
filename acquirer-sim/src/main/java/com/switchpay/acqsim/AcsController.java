package com.switchpay.acqsim;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * §11 step 3: the fake ACS.
 *
 * NR-6: this previously lived in {@code com.switchpay.acquirer}, outside
 * {@link AcquirerSimApplication}'s component-scan root ({@code com.switchpay.acqsim}), so it was
 * never actually registered: every challenge redirect 404'd. It also just echoed the requested
 * action back to whoever called it rather than posting anything to the gateway. It now signs its
 * assertion (§15.3's scheme) and POSTs it to {@code /3ds/callback/{challengeId}}, matching the
 * verification {@code ThreedsCallbackController} now performs.
 */
@RestController
@RequestMapping("/acs")
public class AcsController {

    private final RestClient restClient;
    private final String gatewayUrl;
    private final String threedsSecret;

    public AcsController(RestClient.Builder restClientBuilder,
                         @Value("${gateway.url:http://localhost:8080}") String gatewayUrl,
                         @Value("${switch.threeds.secret:demo-3ds-shared-secret-do-not-use-in-prod}") String threedsSecret) {
        this.restClient = restClientBuilder.build();
        this.gatewayUrl = gatewayUrl;
        this.threedsSecret = threedsSecret;
    }

    @PostMapping("/challenge/{challengeId}")
    public ResponseEntity<Map<String, String>> completeChallenge(
            @PathVariable UUID challengeId, @RequestBody Map<String, String> request) {

        String status = request.getOrDefault("action", "SUCCESS");
        String body = "{\"challengeId\":\"" + challengeId + "\",\"status\":\"" + status + "\"}";
        long timestamp = Instant.now().getEpochSecond();
        String signature = "t=" + timestamp + ",v1=" + Hmac.sign(threedsSecret, timestamp, body);

        restClient.post()
                .uri(gatewayUrl + "/3ds/callback/" + challengeId)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Switch-Signature", signature)
                .body(body)
                .retrieve()
                .toBodilessEntity();

        return ResponseEntity.ok(Map.of("status", status));
    }
}
