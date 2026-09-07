package com.switchpay.risk;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RiskService {

    /**
     * Used only by {@link #evaluate(RiskContext)}, kept for callers with no merchant to read
     * thresholds from (tests, mainly). Production traffic goes through
     * {@link #evaluate(RiskContext, int, int)} with the merchant's own
     * {@code deny_threshold}/{@code challenge_threshold} (NR-9) — these are the values §10.2's
     * demo defaults used before that existed.
     */
    private static final int DEFAULT_DENY_THRESHOLD = 70;
    private static final int DEFAULT_CHALLENGE_THRESHOLD = 40;

    private final List<RiskRule> rules;
    private final RulesetService rulesetService;

    public RiskService(List<RiskRule> rules, RulesetService rulesetService) {
        this.rules = rules;
        this.rulesetService = rulesetService;
    }

    public RiskDecision evaluate(RiskContext context) {
        return evaluate(context, DEFAULT_DENY_THRESHOLD, DEFAULT_CHALLENGE_THRESHOLD, rulesetService.getActiveRuleset());
    }

    public RiskDecision evaluate(RiskContext context, int denyThreshold, int challengeThreshold) {
        return evaluate(context, denyThreshold, challengeThreshold, rulesetService.getActiveRuleset());
    }

    public RiskDecision evaluate(RiskContext context, int denyThreshold, int challengeThreshold, RulesetService.ActiveRuleset activeRuleset) {
        int totalScore = 0;
        List<RiskDecision.RuleAssessment> breakdown = new ArrayList<>();

        for (RiskRule rule : rules) {
            RiskRuleMode mode = activeRuleset.getMode(rule.code());
            if (mode == RiskRuleMode.INACTIVE) {
                continue;
            }

            RuleOutcome outcome = rule.evaluate(context);
            if (mode == RiskRuleMode.ACTIVE) {
                totalScore += outcome.score();
            }
            breakdown.add(new RiskDecision.RuleAssessment(rule.code(), outcome.score(), outcome.reason()));
        }

        String action = RiskDecision.ALLOW;
        if (totalScore >= denyThreshold) {
            action = RiskDecision.DENY;
        } else if (totalScore >= challengeThreshold) {
            action = RiskDecision.CHALLENGE;
        }

        return new RiskDecision(action, totalScore, breakdown, activeRuleset.version());
    }
}
