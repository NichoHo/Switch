package com.switchpay.risk.rules;

import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskRule;
import com.switchpay.risk.RuleOutcome;
import com.switchpay.risk.store.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * §10.1: "many small authorizations from one source with a rising decline rate."
 *
 * // scores on amount + IP velocity alone, not the "rising decline rate" itself: that
 * // needs joining risk_assessment against the payment's eventual AUTH_DECLINED outcome, which
 * // isn't known yet at risk-evaluation time. Add that join the day this needs to be sharper than
 * // a blunt small-amount-velocity signal.
 */
@Component
public class CardTestingPatternRule implements RiskRule {

    private static final long SMALL_AMOUNT_MINOR = 100;   // ~€1.00: the classic testing signature
    private static final long MIN_ATTEMPTS_TO_SCORE = 3;

    private final RiskAssessmentRepository repository;

    public CardTestingPatternRule(RiskAssessmentRepository repository) {
        this.repository = repository;
    }

    @Override
    public String code() { return "CARD_TESTING_PATTERN"; }

    @Override
    public RuleOutcome evaluate(RiskContext ctx) {
        if (ctx.ipAddress() == null || ctx.amountMinor() > SMALL_AMOUNT_MINOR) {
            return new RuleOutcome(0, "ok");
        }
        long count = repository.countSmallAmountsByIpSince(
                ctx.ipAddress(), Instant.now().minus(10, ChronoUnit.MINUTES), SMALL_AMOUNT_MINOR);
        if (count < MIN_ATTEMPTS_TO_SCORE) {
            return new RuleOutcome(0, "ok");
        }
        int score = (int) Math.min(40, count * 8);
        return new RuleOutcome(score, count + " small-value authorization(s) from this IP in the last 10 minutes");
    }
}
