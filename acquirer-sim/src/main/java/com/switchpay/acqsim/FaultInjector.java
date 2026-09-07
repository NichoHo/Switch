package com.switchpay.acqsim;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FaultInjector {
    // Configurable per acquirer via a ConcurrentHashMap
    private final Map<String, FaultConfig> configs = new ConcurrentHashMap<>();
    
    public record FaultConfig(
        int declineRatePercent,      // 0-100
        int errorRatePercent,        // 5xx rate
        int timeoutRatePercent,      // read timeout (black hole)
        long baseLatencyMs,          // base response delay
        long jitterMs,               // random jitter
        int malformedRatePercent     // return garbage JSON
    ) {}
    
    public FaultConfig getConfig(String acquirerId) {
        return configs.getOrDefault(acquirerId, new FaultConfig(0, 0, 0, 100, 50, 0));
    }
    
    public void setConfig(String acquirerId, FaultConfig config) {
        configs.put(acquirerId, config);
    }
}
