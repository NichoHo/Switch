package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import org.springframework.stereotype.Component;

@Component
public class HighRiskCountryRule implements RiskRule {
    @Override
    public String code() { return "HIGH_RISK_COUNTRY"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if ("KP".equals(ctx.ipCountry()) || "KP".equals(ctx.binCountry())) {
            return new RuleOutcome(100, "High risk country");
        }
        return new RuleOutcome(0, "ok");
    }
}
