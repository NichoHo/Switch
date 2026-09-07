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

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class BacktestServiceTest {

    @Autowired
    private BacktestService backtestService;

    @Autowired
    private RiskAssessmentRepository riskRepository;

    @Autowired
    private RulesetService rulesetService;

    @Autowired
    private MerchantRepository merchantRepository;

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
        merchant.setDenyThreshold(70);
        merchant.setChallengeThreshold(40);
        merchantRepository.save(merchant);

        // Define Ruleset v4 with a shadow rule
        rulesetService.createRuleset("v4", Map.of(
            "VELOCITY_CARD_1H", RiskRuleMode.SHADOW,
            "BIN_COUNTRY_MISMATCH", RiskRuleMode.ACTIVE
        ), false);

        // Create historical assessment (old ruleset v1, decision DENY)
        RiskAssessmentEntity old = new RiskAssessmentEntity();
        old.setId(UUID.randomUUID());
        old.setPaymentId(UUID.randomUUID());
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
