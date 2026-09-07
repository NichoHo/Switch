package com.switchpay.recon;

import com.switchpay.recon.store.ReconExceptionEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ReconController {

    private final ReconService reconService;

    public ReconController(ReconService reconService) {
        this.reconService = reconService;
    }

    @GetMapping("/v1/reconciliation/exceptions")
    public List<ReconExceptionEntity> openExceptions() {
        return reconService.openExceptions();
    }

    @PostMapping("/v1/reconciliation/exceptions/{id}/resolve")
    public ReconExceptionEntity resolve(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return reconService.resolve(id, body.get("actor"), body.get("note"));
    }

    /** Secured by network/deploy topology in this demo, matching the other admin endpoints (§22.1). */
    @PostMapping("/admin/recon/run")
    public List<ReconExceptionEntity> run(@RequestParam(required = false) String businessDate) {
        LocalDate date = businessDate != null ? LocalDate.parse(businessDate) : LocalDate.now().minusDays(1);
        return reconService.reconcileDate(date);
    }
}
