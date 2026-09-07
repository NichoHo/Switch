package com.switchpay.risk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchpay.payment.PaymentContext;
import com.switchpay.risk.store.RiskAssessmentEntity;
import com.switchpay.risk.store.RiskAssessmentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Persists what {@link RiskService#evaluate} decided, so §10.2's promise, a decision explainable
 * six months later, is something the database can actually answer, and so velocity rules have
 * history to query (NR-10).
 *
 * Ruleset versioning (§10.3-10.4: shadow mode, backtesting) is Phase 9.
 */
@Component
public class RiskAssessmentRecorder {

    private final RiskAssessmentRepository repository;
    private final ObjectMapper objectMapper;

    public RiskAssessmentRecorder(RiskAssessmentRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void record(UUID paymentId, RiskContext context, RiskDecision decision, 
                        int denyThreshold, int challengeThreshold, String rulesetVersion) {
        RiskAssessmentEntity entity = new RiskAssessmentEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentId(paymentId);
        entity.setMerchantId(context.merchantId());
        entity.setPanFingerprint(context.panFingerprint());
        entity.setIpAddress(context.ipAddress());
        entity.setEmailHash(context.emailHash());

        entity.setBinCountry(context.binCountry());
        entity.setIpCountry(context.ipCountry());
        entity.setNewDevice(context.isNewDevice());
        entity.setAmountMinor(context.amountMinor());
        entity.setScore(decision.score());
        entity.setDecision(decision.action());
        entity.setDenyThreshold(denyThreshold);
        entity.setChallengeThreshold(challengeThreshold);
        entity.setRulesetVersion(rulesetVersion);
        entity.setBreakdown(writeBreakdown(decision));
        entity.setCreatedAt(Instant.now());
        repository.save(entity);
    }

    private String writeBreakdown(RiskDecision decision) {
        try {
            return objectMapper.writeValueAsString(decision.breakdown());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialise risk breakdown", e);
        }
    }
}
