package com.switchpay.settlement;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** NR-14: depends on {@link SettlementQueryService}, not a repository; see its javadoc. */
@RestController
public class SettlementController {

    private final SettlementQueryService queryService;
    private final SettlementBatchJob settlementBatchJob;

    public SettlementController(SettlementQueryService queryService, SettlementBatchJob settlementBatchJob) {
        this.queryService = queryService;
        this.settlementBatchJob = settlementBatchJob;
    }

    @GetMapping("/v1/settlements")
    public List<SettlementBatchEntity> list() {
        return queryService.listBatches();
    }

    @GetMapping("/v1/settlements/{batchId}")
    public ResponseEntity<?> get(@PathVariable UUID batchId) {
        return queryService.findBatchWithItems(batchId)
            .<ResponseEntity<?>>map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Secured by network/deploy topology in this demo, matching the other admin endpoints (§22.1). */
    @PostMapping("/admin/settlement/run")
    public ResponseEntity<Void> run(@RequestParam(required = false) String businessDate) {
        LocalDate date = businessDate != null ? LocalDate.parse(businessDate) : LocalDate.now().minusDays(1);
        settlementBatchJob.runForDate(date);
        return ResponseEntity.accepted().build();
    }
}
