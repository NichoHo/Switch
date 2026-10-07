package com.switchpay.admin;

import com.switchpay.merchant.MerchantEntity;
import com.switchpay.merchant.MerchantRepository;
import com.switchpay.risk.RiskDecision;
import com.switchpay.risk.RiskRuleMode;
import com.switchpay.risk.RulesetService;
import com.switchpay.risk.store.RiskAssessmentEntity;
import com.switchpay.risk.store.RiskAssessmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
public class BacktestServiceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private BacktestService backtestService;

    @Autowired
    private RiskAssessmentRepository riskRepository;

    @Autowired
    private RulesetService rulesetService;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    public void setup() {
        riskRepository.deleteAll();
    }

    @Test
    public void backtestProducesFlipsAndImpact() {
        // Setup Merchant
        MerchantEntity merchant = new MerchantEntity();
        merchant.setId(UUID.randomUUID());
        merchant.setName("Test Merchant");
        merchant.setApiKeyHash("hash");
        merchant.setWebhookSecret("secret");
        merchant.setCreatedAt(java.time.Instant.now());
        merchant.setDenyThreshold(70);
        merchant.setChallengeThreshold(40);
        merchantRepository.save(merchant);

        // risk_assessment.payment_id is a real FK, so the assessment needs a real payment (and card token)
        UUID paymentId = UUID.randomUUID();
        String cardToken = "tok_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO card_token (token, merchant_id, pan_ciphertext, key_version, pan_fingerprint, bin, last4, brand, funding_type, issuer_country, exp_month, exp_year) "
                + "VALUES (?, ?, ?, 1, ?, '42424242', '4242', 'VISA', 'CREDIT', 'GB', 12, 2030)",
                cardToken, merchant.getId(), new byte[]{1, 2, 3}, new byte[]{4, 5, 6});
        jdbc.update("INSERT INTO payment (id, merchant_id, merchant_reference, card_token, amount_minor, currency, state, captured_amount_minor, refunded_amount_minor, version) "
                + "VALUES (?, ?, ?, ?, 5000, 'USD', 'CREATED', 0, 0, 0)",
                paymentId, merchant.getId(), "ref-" + paymentId, cardToken);

        // Define Ruleset v4 with a shadow rule
        rulesetService.createRuleset("v4", Map.of(
            "VELOCITY_CARD_1H", RiskRuleMode.SHADOW,
            "BIN_COUNTRY_MISMATCH", RiskRuleMode.ACTIVE
        ), false);

        // Create historical assessment (old ruleset v1, decision DENY)
        RiskAssessmentEntity old = new RiskAssessmentEntity();
        old.setId(UUID.randomUUID());
        old.setPaymentId(paymentId);
        old.setMerchantId(merchant.getId());
        old.setPanFingerprint("00");
        old.setIpAddress("1.2.3.4");
        old.setAmountMinor(5000); // 50.00
        old.setScore(75);
        old.setDecision(RiskDecision.DENY);
        old.setDenyThreshold(70);
        old.setChallengeThreshold(40);
        old.setRulesetVersion("v1");
        old.setBreakdown("[]");
        old.setCreatedAt(Instant.now());
        old.setBinCountry("US");
        old.setIpCountry("US");
        riskRepository.save(old);

        // Run backtest
        BacktestService.BacktestReport report = backtestService.runBacktest("v4");

        // The backtest will run with real risk service. The old assessment had DENY.
        // With only BIN_COUNTRY_MISMATCH active (and it's US vs US, so 0 score), new score is 0 -> ALLOW.
        // So we expect a DENY -> ALLOW flip.
        assertEquals(1, report.flips.getOrDefault("DENY->ALLOW", 0));
        assertEquals(5000, report.estimatedVolumeImpactMinor); // Gained volume
    }
}
