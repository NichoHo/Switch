package com.switchpay.dispute;

import com.switchpay.dispute.store.DisputeEntity;
import com.switchpay.dispute.store.DisputeRepository;
import com.switchpay.ledger.LedgerService;
import com.switchpay.payment.store.PaymentEntity;
import com.switchpay.payment.store.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class DisputeService {

    private static final int EVIDENCE_WINDOW_DAYS = 14;

    private final DisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final LedgerService ledgerService;

    public DisputeService(DisputeRepository disputeRepository, PaymentRepository paymentRepository, LedgerService ledgerService) {
        this.disputeRepository = disputeRepository;
        this.paymentRepository = paymentRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public UUID open(UUID paymentId, String reasonCode, long amountMinor, String currency) {
        Instant now = Instant.now();
        DisputeEntity entity = new DisputeEntity();
        entity.setId(UUID.randomUUID());
        entity.setPaymentId(paymentId);
        entity.setState(DisputeState.OPENED.name());
        entity.setReasonCode(reasonCode);
        entity.setAmountMinor(amountMinor);
        entity.setCurrency(currency);
        entity.setOpenedAt(now);
        entity.setEvidenceDueAt(now.plus(EVIDENCE_WINDOW_DAYS, ChronoUnit.DAYS));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        disputeRepository.save(entity);

        updatePaymentDisputeState(paymentId, DisputeState.OPENED);
        ledgerService.recordChargebackOpened(paymentId, amountMinor, currency);
        return entity.getId();
    }

    @Transactional
    public void submitEvidence(UUID disputeId) {
        transition(disputeId, Dispute::submitEvidence);
    }

    @Transactional
    public void resolve(UUID disputeId, boolean merchantWon) {
        DisputeEntity entity = transition(disputeId, merchantWon ? Dispute::win : Dispute::lose);
        if (merchantWon) {
            ledgerService.recordChargebackWon(entity.getPaymentId(), entity.getAmountMinor(), entity.getCurrency());
        } else {
            ledgerService.recordChargebackLost(entity.getPaymentId(), entity.getAmountMinor(), entity.getCurrency());
        }
    }

    @Transactional
    public void expire(UUID disputeId) {
        DisputeEntity entity = transition(disputeId, Dispute::expire);
        ledgerService.recordChargebackLost(entity.getPaymentId(), entity.getAmountMinor(), entity.getCurrency());
    }

    private DisputeEntity transition(UUID disputeId, java.util.function.Consumer<Dispute> op) {
        DisputeEntity entity = disputeRepository.findById(disputeId)
            .orElseThrow(() -> new IllegalArgumentException("Dispute not found"));

        Dispute dispute = new Dispute(entity.getId(), entity.getPaymentId(), entity.getReasonCode(),
            entity.getAmountMinor(), entity.getCurrency(), DisputeState.valueOf(entity.getState()), entity.getEvidenceDueAt());
        op.accept(dispute);

        entity.setState(dispute.state().name());
        entity.setUpdatedAt(Instant.now());
        if (dispute.state().isTerminal()) {
            entity.setResolvedAt(Instant.now());
        }
        disputeRepository.save(entity);

        updatePaymentDisputeState(entity.getPaymentId(), dispute.state());
        return entity;
    }

    /** Denormalised for display/filtering only (§14): never read back to make a decision. */
    private void updatePaymentDisputeState(UUID paymentId, DisputeState state) {
        PaymentEntity payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
        payment.setDisputeState(state.name());
        paymentRepository.save(payment);
    }
}
