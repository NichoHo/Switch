package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import org.springframework.stereotype.Component;

@Component
public class AmountAnomalyRule implements RiskRule {
    @Override
    public String code() { return "AMOUNT_ANOMALY"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if (ctx.amountMinor() > 1000000) { // e.g., > $10,000
            return new RuleOutcome(10, "High amount");
        }
        return new RuleOutcome(0, "ok");
    }
}
