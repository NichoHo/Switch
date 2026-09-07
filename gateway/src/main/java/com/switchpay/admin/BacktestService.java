package com.switchpay.admin;

import com.switchpay.merchant.MerchantEntity;
import com.switchpay.merchant.MerchantRepository;
import com.switchpay.risk.RiskContext;
import com.switchpay.risk.RiskDecision;
import com.switchpay.risk.RiskService;
import com.switchpay.risk.RulesetService;
import com.switchpay.risk.store.RiskAssessmentEntity;
import com.switchpay.risk.store.RiskAssessmentRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BacktestService {

    private final RiskAssessmentRepository riskRepository;
    private final MerchantRepository merchantRepository;
    private final RiskService riskService;
    private final RulesetService rulesetService;

    public BacktestService(RiskAssessmentRepository riskRepository, MerchantRepository merchantRepository,
                           RiskService riskService, RulesetService rulesetService) {
        this.riskRepository = riskRepository;
        this.merchantRepository = merchantRepository;
        this.riskService = riskService;
        this.rulesetService = rulesetService;
    }

    public BacktestReport runBacktest(String targetRulesetVersion) {
        RulesetService.ActiveRuleset candidateRuleset = rulesetService.getRuleset(targetRulesetVersion);
        List<RiskAssessmentEntity> history = riskRepository.findAll();
        
        Map<UUID, MerchantEntity> merchants = new HashMap<>();
        
        BacktestReport report = new BacktestReport();
        report.rulesetVersion = targetRulesetVersion;

        int totalBeforeDecline = 0;
        int totalAfterDecline = 0;
        Map<UUID, Integer> merchantTotal = new HashMap<>();
        Map<UUID, Integer> merchantBeforeDecline = new HashMap<>();
        Map<UUID, Integer> merchantAfterDecline = new HashMap<>();

        for (RiskAssessmentEntity old : history) {
            MerchantEntity merchant = merchants.computeIfAbsent(
                    old.getMerchantId(), id -> merchantRepository.findById(id).orElseThrow());

            RiskContext context = new RiskContext(
                    old.getPanFingerprint(), old.getIpAddress(), old.getEmailHash(),
                    old.getBinCountry(), old.getIpCountry(), old.getAmountMinor(),
                    old.getMerchantId(), old.isNewDevice()
            );

            RiskDecision newDecision = riskService.evaluate(
                    context, merchant.getDenyThreshold(), merchant.getChallengeThreshold(), candidateRuleset);

            // Flips
            if (!old.getDecision().equals(newDecision.action())) {
                String flipKey = old.getDecision() + "->" + newDecision.action();
                report.flips.put(flipKey, report.flips.getOrDefault(flipKey, 0) + 1);
            }

            // Volume Impact
            if (RiskDecision.ALLOW.equals(old.getDecision()) && RiskDecision.DENY.equals(newDecision.action())) {
                report.estimatedVolumeImpactMinor -= old.getAmountMinor();
            } else if (RiskDecision.DENY.equals(old.getDecision()) && !RiskDecision.DENY.equals(newDecision.action())) {
                report.estimatedVolumeImpactMinor += old.getAmountMinor();
            }

            // Decline rates
            merchantTotal.put(old.getMerchantId(), merchantTotal.getOrDefault(old.getMerchantId(), 0) + 1);
            if (RiskDecision.DENY.equals(old.getDecision())) {
                totalBeforeDecline++;
                merchantBeforeDecline.put(old.getMerchantId(), merchantBeforeDecline.getOrDefault(old.getMerchantId(), 0) + 1);
            }
            if (RiskDecision.DENY.equals(newDecision.action())) {
                totalAfterDecline++;
                merchantAfterDecline.put(old.getMerchantId(), merchantAfterDecline.getOrDefault(old.getMerchantId(), 0) + 1);
            }

            // Distribution
            String beforeBucket = bucketScore(old.getScore());
            String afterBucket = bucketScore(newDecision.score());
            report.scoreDistributionBefore.put(beforeBucket, report.scoreDistributionBefore.getOrDefault(beforeBucket, 0) + 1);
            report.scoreDistributionAfter.put(afterBucket, report.scoreDistributionAfter.getOrDefault(afterBucket, 0) + 1);
        }

        if (!history.isEmpty()) {
            report.overallDeclineRateDelta = (double) totalAfterDecline / history.size() - (double) totalBeforeDecline / history.size();
        }

        for (UUID merchantId : merchantTotal.keySet()) {
            int total = merchantTotal.get(merchantId);
            double before = (double) merchantBeforeDecline.getOrDefault(merchantId, 0) / total;
            double after = (double) merchantAfterDecline.getOrDefault(merchantId, 0) / total;
            report.merchantDeclineRateDelta.put(merchantId, after - before);
        }

        return report;
    }

    private String bucketScore(int score) {
        if (score < 20) return "0-19";
        if (score < 40) return "20-39";
        if (score < 60) return "40-59";
        if (score < 80) return "60-79";
        if (score < 100) return "80-99";
        return "100+";
    }

    public static class BacktestReport {
        public String rulesetVersion;
        public Map<String, Integer> flips = new HashMap<>();
        public double overallDeclineRateDelta;
        public Map<UUID, Double> merchantDeclineRateDelta = new HashMap<>();
        public long estimatedVolumeImpactMinor;
        public Map<String, Integer> scoreDistributionBefore = new HashMap<>();
        public Map<String, Integer> scoreDistributionAfter = new HashMap<>();
    }
}
