package com.switchpay.routing;

import com.switchpay.contracts.AuthorizationRequest;
import com.switchpay.contracts.AuthorizationResponse;
import com.switchpay.contracts.AuthorizationStatusResponse;
import com.switchpay.contracts.CaptureNotification;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

@Component
public class AcquirerClient {

    private final RestClient restClient;

    public AcquirerClient(RestClient.Builder restClientBuilder) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
            .withConnectTimeout(Duration.ofSeconds(2))
            .withReadTimeout(Duration.ofSeconds(5));
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactories.get(settings);

        this.restClient = restClientBuilder
            .requestFactory(requestFactory)
            .build();
    }

    public record AcquirerCallResult(
        RetrySafety safety,
        AuthorizationResponse response,
        String error
    ) {}
    
    public AcquirerCallResult authorize(String baseUrl, AuthorizationRequest request) {
        return authorize(baseUrl, request, null);
    }

    public AcquirerCallResult authorize(String baseUrl, AuthorizationRequest request, String acquirerId) {
        try {
            var spec = restClient.post()
                .uri(baseUrl + "/authorizations")
                .contentType(MediaType.APPLICATION_JSON);
            
            if (acquirerId != null) {
                spec = spec.header("X-Acquirer-Id", acquirerId);
            }
            
            AuthorizationResponse response = spec
                .body(request)
                .retrieve()
                .body(AuthorizationResponse.class);
            return new AcquirerCallResult(RetrySafety.SAFE, response, null);
        } catch (ResourceAccessException e) {
            Throwable cause = e.getCause();
            if (cause instanceof ConnectException) {
                return new AcquirerCallResult(RetrySafety.SAFE, null, "Connect failure");
            } else if (cause instanceof SocketTimeoutException ste) {
                if (ste.getMessage() != null && ste.getMessage().toLowerCase().contains("connect")) {
                    return new AcquirerCallResult(RetrySafety.SAFE, null, "Connect timeout");
                }
                return new AcquirerCallResult(RetrySafety.UNSAFE, null, "Read timeout");
            }
            return new AcquirerCallResult(RetrySafety.UNSAFE, null, "Resource error: " + e.getMessage());
        } catch (HttpServerErrorException e) {
            int statusCode = e.getStatusCode().value();
            if (statusCode == 502 || statusCode == 503) {
                return new AcquirerCallResult(RetrySafety.SAFE, null, "Server unavailable (" + statusCode + ")");
            }
            return new AcquirerCallResult(RetrySafety.UNSAFE, null, "Server error (" + statusCode + ")");
        } catch (Exception e) {
            // Catches JSON decode errors, malformed responses, etc.
            return new AcquirerCallResult(RetrySafety.UNSAFE, null, "Unexpected: " + e.getMessage());
        }
    }
    
    public AuthorizationStatusResponse statusProbe(String baseUrl, String idempotencyKey) {
        return restClient.get()
            .uri(baseUrl + "/authorizations?idempotencyKey=" + idempotencyKey)
            .retrieve()
            .body(AuthorizationStatusResponse.class);
    }

    public String fetchSettlementFile(String baseUrl, String businessDate) {
        return restClient.get()
            .uri(baseUrl + "/settlement-files/" + businessDate + ".csv")
            .retrieve()
            .body(String.class);
    }

    /** Best-effort: tells acquirer-sim a capture happened, so its settlement file has a matching row. */
    public void notifyCapture(String baseUrl, CaptureNotification notification) {
        restClient.post()
            .uri(baseUrl + "/captures")
            .contentType(MediaType.APPLICATION_JSON)
            .body(notification)
            .retrieve()
            .toBodilessEntity();
    }
}
