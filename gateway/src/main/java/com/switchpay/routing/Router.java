package com.switchpay.routing;

import com.switchpay.contracts.AuthorizationRequest;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.payment.domain.PaymentState;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class Router {
    
    private final AcquirerDirectory acquirerDirectory;
    private final AcquirerClient acquirerClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public Router(AcquirerDirectory acquirerDirectory, AcquirerClient acquirerClient) {
        this.acquirerDirectory = acquirerDirectory;
        this.acquirerClient = acquirerClient;
        
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50)
            .minimumNumberOfCalls(20)
            .slidingWindowSize(20)
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .permittedNumberOfCallsInHalfOpenState(3)
            .build();
            
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(config);
    }

    public record RoutingResult(
        String acquirerId,
        AuthorizationResponse response,
        PaymentState resultState
    ) {}
    
    public RoutingResult route(AuthorizationRequest request, String brand, String currency, String issuerCountry) {
        List<AcquirerDirectory.AcquirerEntry> candidates = acquirerDirectory.findCandidates(brand, currency, issuerCountry);
        
        candidates = candidates.stream()
            .filter(a -> circuitBreaker(a.id()).getState() != CircuitBreaker.State.OPEN)
            .toList();
        
        if (candidates.isEmpty()) {
            throw new NoAcquirerAvailableException();
        }
        
        for (AcquirerDirectory.AcquirerEntry candidate : candidates) {
            CircuitBreaker cb = circuitBreaker(candidate.id());
            long start = System.nanoTime();
            AcquirerClient.AcquirerCallResult result = acquirerClient.authorize(candidate.baseUrl(), request, candidate.id());
            long duration = System.nanoTime() - start;
            
            if (result.response() != null) {
                cb.onSuccess(duration, java.util.concurrent.TimeUnit.NANOSECONDS);
                if ("APPROVED".equals(result.response().status())) {
                    return new RoutingResult(candidate.id(), result.response(), PaymentState.AUTHORIZED);
                } else {
                    return new RoutingResult(candidate.id(), result.response(), PaymentState.AUTH_DECLINED);
                }
            }
            
            cb.onError(duration, java.util.concurrent.TimeUnit.NANOSECONDS, new RuntimeException(result.error()));
            
            if (result.safety() == RetrySafety.UNSAFE) {
                return new RoutingResult(candidate.id(), null, PaymentState.AUTH_UNKNOWN);
            }
            
            // SAFE failure — try next candidate
        }
        
        throw new NoAcquirerAvailableException();
    }
    
    private CircuitBreaker circuitBreaker(String acquirerId) {
        return circuitBreakerRegistry.circuitBreaker(acquirerId);
    }
}
