package com.switchpay.acqsim;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/settlement-faults")
public class SettlementFaultConfigController {

    private final SettlementFaultInjector faultInjector;

    public SettlementFaultConfigController(SettlementFaultInjector faultInjector) {
        this.faultInjector = faultInjector;
    }

    @GetMapping
    public SettlementFaultInjector.FaultConfig getConfig() {
        return faultInjector.getConfig();
    }

    @PutMapping
    public void setConfig(@RequestBody SettlementFaultInjector.FaultConfig config) {
        faultInjector.setConfig(config);
    }
}
