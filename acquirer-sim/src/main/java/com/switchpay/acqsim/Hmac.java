package com.switchpay.acqsim;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * Same {@code t=…,v1=hmac-sha256(t + "." + body)} scheme the gateway uses for outbound webhooks
 * (§15.3) and now verifies on the 3DS callback (NR-6): one algorithm, matched independently on
 * each side rather than shared as a library between two deployables for four lines of code.
 */
final class Hmac {
    private Hmac() {}

    static String sign(String secret, long timestampEpochSeconds, String body) {
        return sign(secret, timestampEpochSeconds + "." + body);
    }

    static String sign(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
