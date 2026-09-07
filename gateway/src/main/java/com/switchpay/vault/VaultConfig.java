package com.switchpay.vault;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VaultConfig {

    @Bean
    public PanCipher panCipher(
            @Value("${switch.vault.key.base64:v/z6zX1Jm32B9gN1m2p3N8zX1Jm32B9gN1m2p3N8zX0=}") String base64Key,
            @Value("${switch.vault.key.version:1}") short keyVersion) {
        return new PanCipher(base64Key, keyVersion);
    }

    @Bean
    public PanFingerprint panFingerprint(
            @Value("${switch.vault.pepper:default-pepper-do-not-use-in-prod}") String pepper) {
        return new PanFingerprint(pepper);
    }
}
