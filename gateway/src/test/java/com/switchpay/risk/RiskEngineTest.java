package com.switchpay.risk;

import com.switchpay.risk.rules.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RiskEngineTest {

    private RulesetService createMockRulesetService() {
        RulesetService rulesetService = mock(RulesetService.class);
        RulesetService.ActiveRuleset activeRuleset = new RulesetService.ActiveRuleset(
                "test-version",
                Map.of(
                        "AMOUNT_ANOMALY", RiskRuleMode.ACTIVE,
                        "HIGH_RISK_COUNTRY", RiskRuleMode.ACTIVE,
                        "BLOCKLIST_CARD", RiskRuleMode.ACTIVE,
                        "NEW_DEVICE_HIGH_AMOUNT", RiskRuleMode.ACTIVE,
                        "BIN_COUNTRY_MISMATCH", RiskRuleMode.ACTIVE
                )
        );
        when(rulesetService.getActiveRuleset()).thenReturn(activeRuleset);
        return rulesetService;
    }

    @Test
    public void testRiskEngineAllow() {
        RiskService riskService = new RiskService(List.of(
                new AmountAnomalyRule(),
                new HighRiskCountryRule()
        ), createMockRulesetService());
        
        RiskContext ctx = new RiskContext("pan1", "ip1", "email1", "US", "US", 1000, UUID.randomUUID(), false);
        RiskDecision decision = riskService.evaluate(ctx);
        
        assertEquals(RiskDecision.ALLOW, decision.action());
        assertEquals(0, decision.score());
    }

    @Test
    public void testRiskEngineDeny() {
        RiskService riskService = new RiskService(List.of(
                new BlocklistCardRule()
        ), createMockRulesetService());
        
        RiskContext ctx = new RiskContext("blocklisted_pan", "ip1", "email1", "US", "US", 1000, UUID.randomUUID(), false);
        RiskDecision decision = riskService.evaluate(ctx);
        
        assertEquals(RiskDecision.DENY, decision.action());
        assertEquals(100, decision.score());
    }

    @Test
    public void testRiskEngineChallenge() {
        RiskService riskService = new RiskService(List.of(
                new NewDeviceHighAmountRule(),
                new BinCountryMismatchRule()
        ), createMockRulesetService());
        
        RiskContext ctx = new RiskContext("pan1", "ip1", "email1", "US", "CA", 60000, UUID.randomUUID(), true);
        RiskDecision decision = riskService.evaluate(ctx);
        
        assertEquals(RiskDecision.CHALLENGE, decision.action());
        assertEquals(50, decision.score()); // 30 (NewDeviceHighAmount) + 20 (BinCountryMismatch)
    }
}
