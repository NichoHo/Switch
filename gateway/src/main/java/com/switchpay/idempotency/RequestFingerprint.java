package com.switchpay.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class RequestFingerprint {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public static byte[] compute(String method, String path, String body) {
        try {
            String canonicalBody;
            if (body != null && !body.isBlank()) {
                // readTree()+writeValueAsString(JsonNode) looks like it canonicalises but does
                // not: ORDER_MAP_ENTRIES_BY_KEYS is consulted by MapSerializer, and a JsonNode
                // tree serialises through JsonNodeSerializer instead, which ignores it: nested
                // objects keep their original key order regardless of the feature flag.
                // Deserialising into Object (LinkedHashMap/List/scalars) and re-serialising that
                // goes through the real MapSerializer at every nesting level, which does sort.
                Object parsed = MAPPER.readValue(body, Object.class);
                canonicalBody = MAPPER.writeValueAsString(parsed);
            } else {
                canonicalBody = "";
            }
            String payload = method.toUpperCase() + "|" + path + "|" + canonicalBody;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute fingerprint", e);
        }
    }
}
