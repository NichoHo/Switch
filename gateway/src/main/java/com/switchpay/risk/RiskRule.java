package com.switchpay.risk;

public interface RiskRule {
    String code();
    RuleOutcome evaluate(RiskContext ctx);
}
