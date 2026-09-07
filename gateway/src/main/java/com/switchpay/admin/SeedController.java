package com.switchpay.admin;

import com.switchpay.common.Currency;
import com.switchpay.merchant.MerchantEntity;
import com.switchpay.merchant.MerchantRepository;
import com.switchpay.payment.PaymentContext;
import com.switchpay.payment.PaymentService;
import com.switchpay.payment.domain.Payment;
import com.switchpay.risk.RiskRuleMode;
import com.switchpay.risk.RulesetService;
import com.switchpay.vault.CardVaultService;
import com.switchpay.vault.store.CardTokenEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/admin")
public class SeedController {

    private final MerchantRepository merchantRepository;
    private final PaymentService paymentService;
    private final RulesetService rulesetService;
    private final CardVaultService cardVaultService;

    public SeedController(MerchantRepository merchantRepository, PaymentService paymentService, RulesetService rulesetService, CardVaultService cardVaultService) {
        this.merchantRepository = merchantRepository;
        this.paymentService = paymentService;
        this.rulesetService = rulesetService;
        this.cardVaultService = cardVaultService;
    }

    @PostMapping("/seed")
    @ResponseStatus(HttpStatus.CREATED)
    public void seed() {
        // 1. Ensure ruleset
        try {
            rulesetService.createRuleset("v1-seed", Map.of(
                    "VELOCITY_CARD_1H", RiskRuleMode.ACTIVE,
                    "BIN_COUNTRY_MISMATCH", RiskRuleMode.ACTIVE
            ), true);
        } catch (IllegalArgumentException e) {
            // Already exists, ignore
        }

        // 2. Create mock merchant
        UUID merchantId = UUID.randomUUID();
        MerchantEntity merchant = new MerchantEntity();
        merchant.setId(merchantId);
        merchant.setName("Demo Merchant");
        merchant.setApiKeyHash("hashed-key");
        merchant.setWebhookSecret("demo-secret");
        merchant.setDenyThreshold(70);
        merchant.setChallengeThreshold(40);
        merchant.setCreatedAt(java.time.Instant.now());
        merchantRepository.save(merchant);

        // 3. Generate 100 payments
        for (int i = 0; i < 100; i++) {
            try {
                // Tokenize card
                CardTokenEntity tokenEntity = cardVaultService.tokenize(
                        merchantId, 
                        "424242424242424" + (i % 10),
                        (short) 12, 
                        (short) 2030
                );

                // Create payment
                Payment payment = paymentService.createPayment(
                        merchantId, 
                        "REF-" + i, 
                        tokenEntity.getToken(), 
                        Currency.EUR, 
                        1000L + (i * 100)
                );

                // Authorize payment
                PaymentContext context = new PaymentContext(
                        "192.168.1." + i, 
                        "user" + i + "@example.com", 
                        null, 
                        "US"
                );
                paymentService.authorize(payment.getId(), context);
            } catch (Exception e) {
                // Ignore exceptions during seed (e.g., risk deny or acquirer fail)
            }
        }
    }
}
