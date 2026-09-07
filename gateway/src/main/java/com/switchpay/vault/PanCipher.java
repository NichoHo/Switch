package com.switchpay.vault;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

public final class PanCipher {
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int GCM_IV_LENGTH = 12;

    private final byte[] key;
    private final short keyVersion;
    private final SecureRandom secureRandom;

    public PanCipher(String base64Key, short keyVersion) {
        // Simple constructor for our purposes; assume base64Key is decoded
        this.key = java.util.Base64.getDecoder().decode(base64Key);
        this.keyVersion = keyVersion;
        this.secureRandom = new SecureRandom();
    }

    public short getKeyVersion() {
        return keyVersion;
    }

    public byte[] encrypt(Pan pan) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);

            byte[] cipherText = cipher.doFinal(pan.getValue().getBytes(StandardCharsets.UTF_8));
            
            // Prepend IV to ciphertext
            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);
            return byteBuffer.array();
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public Pan decrypt(byte[] ivAndCiphertext) {
        try {
            ByteBuffer byteBuffer = ByteBuffer.wrap(ivAndCiphertext);
            byte[] iv = new byte[GCM_IV_LENGTH];
            byteBuffer.get(iv);
            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);

            byte[] plainText = cipher.doFinal(cipherText);
            return new Pan(new String(plainText, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }
}
