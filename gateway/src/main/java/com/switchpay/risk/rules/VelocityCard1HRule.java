package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import com.switchpay.risk.store.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class VelocityCard1HRule implements RiskRule {

    private final RiskAssessmentRepository repository;

    public VelocityCard1HRule(RiskAssessmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String code() { return "VELOCITY_CARD_1H"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if (ctx.panFingerprint() == null) {
            return new RuleOutcome(0, "ok");
        }
        long count = repository.countByPanFingerprintSince(
                ctx.panFingerprint(), Instant.now().minus(1, ChronoUnit.HOURS));
        if (count == 0) {
            return new RuleOutcome(0, "ok");
        }
        int score = (int) Math.min(35, count * 7);
        return new RuleOutcome(score, count + " authorization(s) on this card in the last hour");
    }
}
