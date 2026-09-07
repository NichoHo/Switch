package com.switchpay.vault;

import com.switchpay.vault.store.CardTokenEntity;
import com.switchpay.vault.store.CardTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CardVaultServiceTest {

    private CardTokenRepository repository;
    private PanCipher panCipher;
    private PanFingerprint panFingerprint;
    private CardVaultService service;

    @BeforeEach
    void setUp() {
        repository = mock(CardTokenRepository.class);
        // Using an arbitrary base64 32-byte key for testing
        panCipher = new PanCipher("v/z6zX1Jm32B9gN1m2p3N8zX1Jm32B9gN1m2p3N8zX0=", (short) 1);
        panFingerprint = new PanFingerprint("test-pepper");
        service = new CardVaultService(repository, panCipher, panFingerprint);
    }

    @Test
    void tokenize_validTestVisa_savesToken() {
        Pan pan = new Pan("4242424242424242");
        UUID merchantId = UUID.randomUUID();

        when(repository.save(any(CardTokenEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardTokenEntity entity = service.tokenize(merchantId, pan, (short) 12, (short) 2030);

        assertNotNull(entity.getToken());
        assertTrue(entity.getToken().startsWith("tok_"));
        assertEquals(merchantId, entity.getMerchantId());
        assertEquals("42424242", entity.getBin());
        assertEquals("4242", entity.getLast4());
        assertEquals("VISA", entity.getBrand());
        assertEquals("CREDIT", entity.getFundingType());
        assertEquals("GB", entity.getIssuerCountry());
        
        // Assert encryption works by decrypting
        Pan decrypted = panCipher.decrypt(entity.getPanCiphertext());
        assertEquals(pan, decrypted);
    }

    @Test
    void tokenize_invalidLuhn_throws() {
        // Last digit is wrong for this sequence
        Pan pan = new Pan("4242424242424241");
        UUID merchantId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            service.tokenize(merchantId, pan, (short) 12, (short) 2030));
        
        assertEquals("pan_invalid_luhn", ex.getMessage());
    }

    @Test
    void tokenize_nonTestBin_throws() {
        // A valid luhn but not a test BIN
        Pan pan = new Pan("4111111111111111");
        UUID merchantId = UUID.randomUUID();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> 
            service.tokenize(merchantId, pan, (short) 12, (short) 2030));
        
        assertEquals("pan_not_a_test_card", ex.getMessage());
    }
}
