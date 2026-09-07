package com.switchpay.payment.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchpay.payment.PaymentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * §11 step 4: the fake ACS posts its assertion here.
 *
 * NR-6: this used to trust a bare {@code ?status=} query parameter. Anyone who could guess a
 * challenge UUID could grant liability shift on someone else's payment. Every assertion is now
 * required to carry a {@code Switch-Signature} header (§15.3's scheme, reused rather than
 * inventing a second one) and land inside a 5-minute freshness window.
 *
 * NR-14: everything past "is this request authentic" (challenge-id binding, state, expiry,
 * driving the payment) lives in {@code PaymentService.completeThreedsChallenge()}. This
 * controller's only jobs are the two things that genuinely require {@link HttpServletRequest}:
 * reading the raw body (needed to verify the signature over exactly the bytes that were signed)
 * and checking that signature.
 */
@RestController
@RequestMapping("/3ds")
public class ThreedsCallbackController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PaymentService paymentService;
    private final String threedsSecret;

    public ThreedsCallbackController(PaymentService paymentService,
                                     @Value("${switch.threeds.secret:demo-3ds-shared-secret-do-not-use-in-prod}") String threedsSecret) {
        this.paymentService = paymentService;
        this.threedsSecret = threedsSecret;
    }

    @PostMapping("/callback/{challengeId}")
    public ResponseEntity<String> callback(@PathVariable UUID challengeId, HttpServletRequest request) throws IOException {
        String rawBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        ThreedsSignature.verify(threedsSecret, request.getHeader("Switch-Signature"), rawBody);

        JsonNode body = MAPPER.readTree(rawBody);
        UUID signedChallengeId = UUID.fromString(body.get("challengeId").asText());
        String status = body.get("status").asText();

        boolean success = paymentService.completeThreedsChallenge(challengeId, signedChallengeId, status);
        return success ? ResponseEntity.ok("ok") : ResponseEntity.badRequest().body("failed");
    }
}
