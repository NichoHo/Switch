package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import org.springframework.stereotype.Component;

@Component
public class NewDeviceHighAmountRule implements RiskRule {
    @Override
    public String code() { return "NEW_DEVICE_HIGH_AMOUNT"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if (ctx.isNewDevice() && ctx.amountMinor() > 50000) {
            return new RuleOutcome(30, "New device and high amount");
        }
        return new RuleOutcome(0, "ok");
    }
}
