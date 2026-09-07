package com.switchpay.acqsim;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/faults")
public class FaultConfigController {
    
    private final FaultInjector faultInjector;

    public FaultConfigController(FaultInjector faultInjector) {
        this.faultInjector = faultInjector;
    }

    @GetMapping("/{acquirerId}")
    public FaultInjector.FaultConfig getConfig(@PathVariable String acquirerId) {
        return faultInjector.getConfig(acquirerId);
    }

    @PutMapping("/{acquirerId}")
    public void setConfig(@PathVariable String acquirerId, @RequestBody FaultInjector.FaultConfig config) {
        faultInjector.setConfig(acquirerId, config);
    }
}
