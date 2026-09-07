package com.switchpay.risk;

import java.util.List;

/**
 * §10.2: "persisted with its full per-rule breakdown... A decision you cannot explain six months
 * later is not a decision." {@code breakdown} carries every rule's outcome, not only the ones
 * that fired: seeing that a rule ran and scored zero is part of the explanation too.
 */
public record RiskDecision(String action, int score, List<RuleAssessment> breakdown, String rulesetVersion) {
    public static final String ALLOW = "ALLOW";
    public static final String CHALLENGE = "CHALLENGE";
    public static final String DENY = "DENY";

    public record RuleAssessment(String code, int score, String reason) {}
}
