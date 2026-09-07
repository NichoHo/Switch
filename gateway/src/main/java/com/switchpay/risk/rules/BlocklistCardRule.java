package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import org.springframework.stereotype.Component;

@Component
public class BlocklistCardRule implements RiskRule {
    @Override
    public String code() { return "BLOCKLIST_CARD"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if ("blocklisted_pan".equals(ctx.panFingerprint())) {
            return new RuleOutcome(100, "Card is blocklisted");
        }
        return new RuleOutcome(0, "ok");
    }
}
