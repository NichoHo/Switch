package com.switchpay.merchant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * §16: API keys are "stored as a hash". A presented key is high-entropy and single-purpose
 * (unlike a password), so a plain SHA-256 lookup hash is the right tool — no salt needed to
 * defend against guessing, and a salt would break the O(1) {@code findByApiKeyHash} lookup this
 * exists to support.
 */
public final class ApiKeyHasher {
    private ApiKeyHasher() {}

    public static String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
