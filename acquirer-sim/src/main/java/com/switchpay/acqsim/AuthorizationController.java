package com.switchpay.acqsim;

import com.switchpay.contracts.AuthorizationRequest;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.contracts.AuthorizationStatusResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/authorizations")
public class AuthorizationController {

    private final FaultInjector faultInjector;
    private final Map<String, AuthorizationResponse> store = new ConcurrentHashMap<>();

    public AuthorizationController(FaultInjector faultInjector) {
        this.faultInjector = faultInjector;
    }

    @PostMapping
    public ResponseEntity<?> authorize(
            @RequestHeader(value = "X-Acquirer-Id", defaultValue = "default") String acquirerId,
            @RequestBody AuthorizationRequest request) throws InterruptedException {

        FaultInjector.FaultConfig config = faultInjector.getConfig(acquirerId);

        // 2. Apply base latency + jitter
        long delay = config.baseLatencyMs() + (config.jitterMs() > 0 ? ThreadLocalRandom.current().nextLong(config.jitterMs()) : 0);
        if (delay > 0) {
            Thread.sleep(delay);
        }

        // 3. Roll dice for fault injection
        int roll = ThreadLocalRandom.current().nextInt(100);
        int currentThreshold = 0;
        
        currentThreshold += config.timeoutRatePercent();
        // a. timeout rate -> Thread.sleep(60_000)
        if (roll < currentThreshold) {
            Thread.sleep(60_000);
            return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).build();
        }
        
        currentThreshold += config.errorRatePercent();
        // b. error rate -> return 503
        if (roll < currentThreshold) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        
        currentThreshold += config.malformedRatePercent();
        // c. malformed rate -> return 200 with garbage body
        if (roll < currentThreshold) {
            return ResponseEntity.ok("{ \"garbage\": true, \"broken_json\": ");
        }

        // 4. Check idempotency
        if (request.idempotencyKey() != null && store.containsKey(request.idempotencyKey())) {
            return ResponseEntity.ok(store.get(request.idempotencyKey()));
        }

        // 5. Roll dice for decline rate -> DECLINED
        int declineRoll = ThreadLocalRandom.current().nextInt(100);
        AuthorizationResponse response;
        
        if (declineRoll < config.declineRatePercent()) {
            response = new AuthorizationResponse("DECLINED", UUID.randomUUID().toString(), null, "insufficient_funds");
        } else {
            // 6. Otherwise -> APPROVED
            response = new AuthorizationResponse("APPROVED", UUID.randomUUID().toString(), UUID.randomUUID().toString().substring(0, 6), null);
        }

        // 7. Store the response
        if (request.idempotencyKey() != null) {
            store.put(request.idempotencyKey(), response);
        }

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<AuthorizationStatusResponse> getStatus(@RequestParam String idempotencyKey) {
        AuthorizationResponse response = store.get(idempotencyKey);
        
        if (response != null) {
            return ResponseEntity.ok(new AuthorizationStatusResponse(response.status(), response.acquirerReference(), response.authCode(), response.declineReason()));
        } else {
            return ResponseEntity.ok(new AuthorizationStatusResponse("NOT_FOUND", null, null, null));
        }
    }
}
