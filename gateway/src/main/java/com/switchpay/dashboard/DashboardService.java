package com.switchpay.dashboard;

import com.switchpay.common.Currency;
import com.switchpay.ledger.TrialBalanceService;
import com.switchpay.payment.store.PaymentEntity;
import com.switchpay.payment.store.PaymentEventEntity;
import com.switchpay.payment.store.PaymentEventRepository;
import com.switchpay.payment.store.PaymentRepository;
import com.switchpay.recon.store.ReconExceptionRepository;
import com.switchpay.risk.store.RiskAssessmentEntity;
import com.switchpay.risk.store.RiskAssessmentRepository;
import com.switchpay.routing.AcquirerDirectory;
import com.switchpay.settlement.SettlementBatchEntity;
import com.switchpay.settlement.SettlementBatchRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    /** The console is a monitoring surface, not an export. Keep every list bounded. */
    private static final int PAGE_SIZE = 50;

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final RiskAssessmentRepository riskRepository;
    private final SettlementBatchRepository settlementRepository;
    private final ReconExceptionRepository reconExceptionRepository;
    private final TrialBalanceService trialBalanceService;
    private final AcquirerDirectory acquirerDirectory;

    public DashboardService(PaymentRepository paymentRepository,
                            PaymentEventRepository paymentEventRepository,
                            RiskAssessmentRepository riskRepository,
                            SettlementBatchRepository settlementRepository,
                            ReconExceptionRepository reconExceptionRepository,
                            TrialBalanceService trialBalanceService,
                            AcquirerDirectory acquirerDirectory) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.riskRepository = riskRepository;
        this.settlementRepository = settlementRepository;
        this.reconExceptionRepository = reconExceptionRepository;
        this.trialBalanceService = trialBalanceService;
        this.acquirerDirectory = acquirerDirectory;
    }

    public List<PaymentEntity> getLatestPayments() {
        return paymentRepository.findAll(
            PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    public Optional<PaymentEntity> findPayment(UUID id) {
        return paymentRepository.findById(id);
    }

    /** The append-only event log for one payment, oldest first. */
    public List<PaymentEventEntity> getEvents(UUID paymentId) {
        return paymentEventRepository.findByPaymentIdOrderBySeqAsc(paymentId);
    }

    public List<RiskAssessmentEntity> getLatestRiskAssessments() {
        return riskRepository.findAll(
            PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }

    public List<SettlementBatchEntity> getLatestSettlements() {
        return settlementRepository.findAllByOrderByCreatedAtDesc();
    }

    public long countOpenReconExceptions() {
        return reconExceptionRepository.findByResolvedAtIsNull().size();
    }

    /**
     * Trial balance per currency. A currency with no entries sums to zero, which is
     * also what "balanced" looks like, so an empty ledger reads as balanced. That is
     * correct: there is nothing out of balance yet.
     */
    public Map<String, Long> getTrialBalances() {
        Map<String, Long> balances = new LinkedHashMap<>();
        for (Currency currency : Currency.values()) {
            balances.put(currency.name(), trialBalanceService.getTrialBalance(currency.name()));
        }
        return balances;
    }

    public List<AcquirerDirectory.AcquirerEntry> getAcquirers() {
        return acquirerDirectory.findAll();
    }
}
