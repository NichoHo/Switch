package com.switchpay.vault;

import com.switchpay.vault.store.CardTokenEntity;
import com.switchpay.vault.store.CardTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class CardVaultService {

    private final CardTokenRepository repository;
    private final PanCipher panCipher;
    private final PanFingerprint panFingerprint;
    private final SecureRandom secureRandom = new SecureRandom();

    public CardVaultService(CardTokenRepository repository, PanCipher panCipher, PanFingerprint panFingerprint) {
        this.repository = repository;
        this.panCipher = panCipher;
        this.panFingerprint = panFingerprint;
    }

    /**
     * The entry point everything outside this package uses. {@link Pan} is deliberately not in
     * the signature: §8.2's ArchUnit rule forbids any class outside {@code com.switchpay.vault..}
     * from referencing it, so a caller that cannot name the type cannot hold one either. The
     * plaintext exists only for the span of this call.
     */
    @Transactional
    public CardTokenEntity tokenize(UUID merchantId, String pan, short expMonth, short expYear) {
        return tokenize(merchantId, new Pan(pan), expMonth, expYear);
    }

    @Transactional
    public CardTokenEntity tokenize(UUID merchantId, Pan pan, short expMonth, short expYear) {
        if (!Luhn.isValid(pan)) {
            throw new IllegalArgumentException("pan_invalid_luhn");
        }

        BinDirectory.BinMetadata meta = BinDirectory.lookup(pan)
                .orElseThrow(() -> new IllegalArgumentException("pan_not_a_test_card"));

        String panStr = pan.getValue();
        String last4 = panStr.substring(panStr.length() - 4);

        byte[] ciphertext = panCipher.encrypt(pan);
        byte[] fingerprint = panFingerprint.generate(pan);

        CardTokenEntity entity = new CardTokenEntity();
        entity.setToken(generateTokenId());
        entity.setMerchantId(merchantId);
        entity.setPanCiphertext(ciphertext);
        entity.setKeyVersion(panCipher.getKeyVersion());
        entity.setPanFingerprint(fingerprint);
        entity.setBin(meta.bin());
        entity.setBrand(meta.brand());
        entity.setFundingType(meta.fundingType());
        entity.setIssuerCountry(meta.issuerCountry());
        entity.setLast4(last4);
        entity.setExpMonth(expMonth);
        entity.setExpYear(expYear);
        entity.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        return repository.save(entity);
    }

    private String generateTokenId() {
        byte[] randomBytes = new byte[16];
        secureRandom.nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder("tok_");
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
