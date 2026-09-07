package com.switchpay.dispute;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    /** Demo-only: real disputes arrive as acquirer notifications, which this system doesn't yet ingest. */
    @PostMapping("/admin/disputes")
    public ResponseEntity<Map<String, UUID>> open(@RequestBody Map<String, Object> body) {
        UUID paymentId = UUID.fromString((String) body.get("paymentId"));
        String reasonCode = (String) body.get("reasonCode");
        long amountMinor = ((Number) body.get("amountMinor")).longValue();
        String currency = (String) body.get("currency");
        UUID id = disputeService.open(paymentId, reasonCode, amountMinor, currency);
        return ResponseEntity.ok(Map.of("id", id));
    }

    @PostMapping("/v1/disputes/{id}/evidence")
    public ResponseEntity<Void> submitEvidence(@PathVariable UUID id) {
        disputeService.submitEvidence(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/v1/disputes/{id}/resolve")
    public ResponseEntity<Void> resolve(@PathVariable UUID id, @RequestBody Map<String, Boolean> body) {
        disputeService.resolve(id, Boolean.TRUE.equals(body.get("merchantWon")));
        return ResponseEntity.ok().build();
    }
}
