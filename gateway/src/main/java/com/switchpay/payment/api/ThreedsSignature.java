package com.switchpay.payment.api;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * §11 step 5 / NR-6: the ACS assertion posted back to {@code /3ds/callback} must be signed, not
 * trusted on the strength of a query parameter. Same {@code t=…,v1=hmac-sha256(t + "." + body)}
 * scheme as §15.3's webhook signature: one convention, two callers, rather than inventing a
 * second signing format for what is structurally the same problem.
 */
final class ThreedsSignature {

    private static final Pattern HEADER = Pattern.compile("t=(\\d+),v1=([0-9a-f]+)");
    static final long FRESHNESS_WINDOW_SECONDS = 300;   // matches §15.3's webhook replay window

    private ThreedsSignature() {}

    static String sign(String secret, long timestampEpochSeconds, String body) {
        return hmac(secret, timestampEpochSeconds + "." + body);
    }

    /** @throws IllegalArgumentException with a §17 error code on any verification failure. */
    static void verify(String secret, String header, String body) {
        if (header == null) {
            throw new IllegalArgumentException("threeds_signature_missing");
        }
        Matcher matcher = HEADER.matcher(header);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("threeds_signature_invalid");
        }
        long timestamp = Long.parseLong(matcher.group(1));
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > FRESHNESS_WINDOW_SECONDS) {
            throw new IllegalArgumentException("threeds_signature_expired");
        }
        String expected = sign(secret, timestamp, body);
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), matcher.group(2).getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("threeds_signature_invalid");
        }
    }

    private static String hmac(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
