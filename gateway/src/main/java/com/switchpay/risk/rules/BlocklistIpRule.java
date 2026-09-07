package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import org.springframework.stereotype.Component;

@Component
public class BlocklistIpRule implements RiskRule {
    @Override
    public String code() { return "BLOCKLIST_IP"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if ("192.168.1.100".equals(ctx.ipAddress())) {
            return new RuleOutcome(100, "IP is blocklisted");
        }
        return new RuleOutcome(0, "ok");
    }
}
