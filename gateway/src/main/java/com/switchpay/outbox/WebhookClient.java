package com.switchpay.outbox;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class WebhookClient {

    private final RestClient restClient;

    public WebhookClient(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public void sendWebhook(OutboxEventEntity event, String webhookUrl, String webhookSecret) {
        String payload = event.getPayload();
        long timestamp = Instant.now().getEpochSecond();
        String toSign = timestamp + "." + payload;
        
        String signature = hmacSha256(webhookSecret, toSign);
        String headerValue = "t=" + timestamp + ",v1=" + signature;

        restClient.post()
                .uri(webhookUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Switch-Signature", headerValue)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private String hmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA256", e);
        }
    }
}
