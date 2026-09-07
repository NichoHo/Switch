package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import com.switchpay.risk.store.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class VelocityIp24HRule implements RiskRule {

    private final RiskAssessmentRepository repository;

    public VelocityIp24HRule(RiskAssessmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String code() { return "VELOCITY_IP_24H"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if (ctx.ipAddress() == null) {
            return new RuleOutcome(0, "ok");
        }
        long distinctCards = repository.countDistinctCardsByIpSince(
                ctx.ipAddress(), Instant.now().minus(24, ChronoUnit.HOURS));
        if (distinctCards == 0) {
            return new RuleOutcome(0, "ok");
        }
        int score = (int) Math.min(30, distinctCards * 10);
        return new RuleOutcome(score, distinctCards + " distinct card(s) from this IP in the last 24h");
    }
}
