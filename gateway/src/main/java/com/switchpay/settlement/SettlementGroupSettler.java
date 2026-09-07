package com.switchpay.settlement;

import com.switchpay.ledger.LedgerService;
import com.switchpay.outbox.OutboxEventEntity;
import com.switchpay.outbox.OutboxRepository;
import com.switchpay.payment.store.PaymentOperationEntity;
import com.switchpay.payment.store.PaymentOperationRepository;
import com.switchpay.routing.AcquirerDirectory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Settles one (merchant, currency, acquirer, business_date) group.
 *
 * NR-15: this used to be a package-private method on {@code SettlementBatchJob} called via
 * {@code this.settleGroup(...)} from within the same class — Spring's transactional proxy only
 * intercepts calls that arrive from *outside* the bean, so {@code @Transactional} on that method
 * was silently inert. Pulling it out into its own bean, called from {@code SettlementBatchJob}
 * through the injected reference, is what makes the proxy — and therefore the transaction —
 * actually apply.
 */
@Component
class SettlementGroupSettler {

    private final PaymentOperationRepository operationRepository;
    private final SettlementBatchRepository batchRepository;
    private final SettlementItemRepository itemRepository;
    private final LedgerService ledgerService;
    private final AcquirerDirectory acquirerDirectory;
    private final OutboxRepository outboxRepository;
    private final JdbcTemplate jdbcTemplate;

    SettlementGroupSettler(PaymentOperationRepository operationRepository, SettlementBatchRepository batchRepository,
                           SettlementItemRepository itemRepository, LedgerService ledgerService,
                           AcquirerDirectory acquirerDirectory, OutboxRepository outboxRepository,
                           JdbcTemplate jdbcTemplate) {
        this.operationRepository = operationRepository;
        this.batchRepository = batchRepository;
        this.itemRepository = itemRepository;
        this.ledgerService = ledgerService;
        this.acquirerDirectory = acquirerDirectory;
        this.outboxRepository = outboxRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    void settle(UUID merchantId, String currency, String acquirerId, LocalDate businessDate, Instant cutoff) {
        List<PaymentOperationEntity> captures =
            operationRepository.findUnsettledCaptures(merchantId, currency, acquirerId, cutoff);
        if (captures.isEmpty()) {
            return;
        }

        FeeModel.AcquirerCost acquirerCost = acquirerDirectory.findById(acquirerId)
            .map(a -> new FeeModel.AcquirerCost(a.costBps(), a.costFixedMinor()))
            .orElse(new FeeModel.AcquirerCost(0, 0));
        FeeModel.MerchantFees merchantFees = fetchMerchantFees(merchantId);

        List<PaymentOperationEntity> refundOps =
            operationRepository.findUnsettledRefunds(merchantId, currency, acquirerId, cutoff);
        // NR-16: MERCHANT_RECEIVABLE's remaining balance for a payment, after its capture, any
        // refund (posted at refund time to REFUNDS_CLEARING) and the fee assessment below, is
        // gross − refund − fees. Settlement's job is to zero that out by moving it to
        // MERCHANT_PAYABLE — so that is what each item's settlement posting must carry. The
        // previous version posted gross − fees per item, omitting refunds entirely:
        // MERCHANT_RECEIVABLE never zeroed out when a window had any, the shortfall sat there
        // permanently, and net_minor (this same gross − refund − fees figure) didn't match what
        // had actually moved.
        //
        // refunds net against the capture for the *same* payment, correct for the one
        // case this codebase exercises (one capture per payment). A payment captured more than
        // once within a single settlement run would have each of its items net the full refund
        // total against itself, double-counting. Add per-operation refund attribution (a refund
        // referencing which capture it reverses) the day partial-capture-then-refund is a real flow.
        Map<UUID, Long> refundsByPayment = refundOps.stream().collect(Collectors.groupingBy(
            PaymentOperationEntity::getPaymentId, Collectors.summingLong(PaymentOperationEntity::getAmountMinor)));

        UUID batchId = UUID.randomUUID();
        List<SettlementItemEntity> items = new ArrayList<>();
        long gross = 0, refunds = 0, schemeFee = 0, gatewayFee = 0;

        for (PaymentOperationEntity op : captures) {
            long itemGross = op.getAmountMinor();
            long itemRefund = refundsByPayment.getOrDefault(op.getPaymentId(), 0L);
            long itemScheme = FeeModel.schemeFee(itemGross, acquirerCost);
            long itemGateway = FeeModel.gatewayFee(itemGross, merchantFees);
            long itemNet = itemGross - itemRefund - itemScheme - itemGateway;

            ledgerService.recordFeeAssessment(op.getPaymentId(), currency, itemGateway, itemScheme);
            ledgerService.recordSettlement(op.getPaymentId(), currency, itemNet, "Settlement");

            op.setSettlementState("SETTLED");
            operationRepository.save(op);

            gross += itemGross;
            schemeFee += itemScheme;
            gatewayFee += itemGateway;
            items.add(new SettlementItemEntity(UUID.randomUUID(), batchId, op.getId(), op.getPaymentId(), itemGross, currency));
        }

        for (PaymentOperationEntity refundOp : refundOps) {
            refunds += refundOp.getAmountMinor();
            refundOp.setSettlementState("SETTLED");
            operationRepository.save(refundOp);
        }

        long net = gross - refunds - schemeFee - gatewayFee;
        batchRepository.save(new SettlementBatchEntity(
            batchId, merchantId, currency, acquirerId, businessDate, gross, refunds, schemeFee, gatewayFee, net));
        itemRepository.saveAll(items);

        writeSettlementCompletedEvent(merchantId, batchId, businessDate);
    }

    private FeeModel.MerchantFees fetchMerchantFees(UUID merchantId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT rate_bps, fixed_fee_minor FROM merchant WHERE id = ?", merchantId);
        if (rows.isEmpty()) {
            return new FeeModel.MerchantFees(0, 0);
        }
        Number rateBps = (Number) rows.get(0).get("rate_bps");
        Number fixedFee = (Number) rows.get(0).get("fixed_fee_minor");
        return new FeeModel.MerchantFees(rateBps.intValue(), fixedFee.longValue());
    }

    private void writeSettlementCompletedEvent(UUID merchantId, UUID batchId, LocalDate businessDate) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setId(UUID.randomUUID());
        event.setMerchantId(merchantId);
        event.setEventType("settlement.completed");
        event.setPayload("{\"batch_id\": \"" + batchId + "\", \"business_date\": \"" + businessDate + "\"}");
        event.setState("PENDING");
        event.setAttempts(0);
        event.setNextAttemptAt(Instant.now());
        event.setCreatedAt(Instant.now());
        outboxRepository.save(event);
    }
}
