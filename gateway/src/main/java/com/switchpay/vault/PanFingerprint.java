package com.switchpay.vault;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public final class PanFingerprint {

    private final String pepper;

    public PanFingerprint(String pepper) {
        this.pepper = pepper;
    }

    public byte[] generate(Pan pan) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKeySpec);
            return mac.doFinal(pan.getValue().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PAN fingerprint", e);
        }
    }
}
