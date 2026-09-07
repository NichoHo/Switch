package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import org.springframework.stereotype.Component;

@Component
public class BinCountryMismatchRule implements RiskRule {
    @Override
    public String code() { return "BIN_COUNTRY_MISMATCH"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if (ctx.binCountry() != null && ctx.ipCountry() != null 
            && !ctx.binCountry().equals(ctx.ipCountry())) {
            return new RuleOutcome(20, "Mismatch between BIN and IP country");
        }
        return new RuleOutcome(0, "ok");
    }
}
