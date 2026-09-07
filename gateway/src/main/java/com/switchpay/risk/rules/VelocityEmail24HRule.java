package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import com.switchpay.risk.store.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class VelocityEmail24HRule implements RiskRule {

    private final RiskAssessmentRepository repository;

    public VelocityEmail24HRule(RiskAssessmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String code() { return "VELOCITY_EMAIL_24H"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if (ctx.emailHash() == null) {
            return new RuleOutcome(0, "ok");
        }
        long count = repository.countByEmailHashSince(ctx.emailHash(), Instant.now().minus(24, ChronoUnit.HOURS));
        if (count == 0) {
            return new RuleOutcome(0, "ok");
        }
        int score = (int) Math.min(20, count * 4);
        return new RuleOutcome(score, count + " authorization(s) for this email in the last 24h");
    }
}
