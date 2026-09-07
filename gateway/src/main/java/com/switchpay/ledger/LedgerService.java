package com.switchpay.ledger;

import com.switchpay.ledger.store.LedgerEntryEntity;
import com.switchpay.ledger.store.LedgerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public void recordCapture(UUID paymentId, long amount, String currency) {
        UUID transactionId = UUID.randomUUID();
        
        LedgerEntryEntity debit = new LedgerEntryEntity(
            transactionId, 
            ChartOfAccounts.ACQUIRER_CLEARING.getId(), 
            Direction.DEBIT, 
            amount, 
            currency, 
            paymentId, 
            "Capture"
        );
        
        LedgerEntryEntity credit = new LedgerEntryEntity(
            transactionId, 
            ChartOfAccounts.MERCHANT_RECEIVABLE.getId(), 
            Direction.CREDIT, 
            amount, 
            currency, 
            paymentId, 
            "Capture"
        );
        
        ledgerRepository.saveAll(List.of(debit, credit));
    }

    @Transactional
    public void recordRefund(UUID paymentId, long amount, String currency) {
        UUID transactionId = UUID.randomUUID();

        LedgerEntryEntity debit = new LedgerEntryEntity(
            transactionId,
            ChartOfAccounts.MERCHANT_RECEIVABLE.getId(),
            Direction.DEBIT,
            amount,
            currency,
            paymentId,
            "Refund"
        );

        LedgerEntryEntity credit = new LedgerEntryEntity(
            transactionId,
            ChartOfAccounts.REFUNDS_CLEARING.getId(),
            Direction.CREDIT,
            amount,
            currency,
            paymentId,
            "Refund"
        );

        ledgerRepository.saveAll(List.of(debit, credit));
    }

    /** Debit MERCHANT_RECEIVABLE for the total fee, split as revenue and scheme cost (§12.2). */
    @Transactional
    public void recordFeeAssessment(UUID paymentId, String currency, long gatewayFeeMinor, long schemeFeeMinor) {
        long total = gatewayFeeMinor + schemeFeeMinor;
        if (total <= 0) {
            return;
        }
        UUID transactionId = UUID.randomUUID();
        List<LedgerEntryEntity> entries = new java.util.ArrayList<>();
        entries.add(new LedgerEntryEntity(transactionId, ChartOfAccounts.MERCHANT_RECEIVABLE.getId(),
            Direction.DEBIT, total, currency, paymentId, "Fee assessment"));
        if (gatewayFeeMinor > 0) {
            entries.add(new LedgerEntryEntity(transactionId, ChartOfAccounts.GATEWAY_REVENUE.getId(),
                Direction.CREDIT, gatewayFeeMinor, currency, paymentId, "Gateway fee"));
        }
        if (schemeFeeMinor > 0) {
            entries.add(new LedgerEntryEntity(transactionId, ChartOfAccounts.SCHEME_FEES.getId(),
                Direction.CREDIT, schemeFeeMinor, currency, paymentId, "Scheme fee"));
        }
        ledgerRepository.saveAll(entries);
    }

    /** Moves settled net funds from receivable to payable, ready for payout. */
    @Transactional
    public void recordSettlement(UUID paymentId, String currency, long netMinor, String memo) {
        if (netMinor == 0) {
            return;
        }
        UUID transactionId = UUID.randomUUID();
        boolean positive = netMinor > 0;
        long amount = Math.abs(netMinor);
        UUID debitAccount = positive ? ChartOfAccounts.MERCHANT_RECEIVABLE.getId() : ChartOfAccounts.MERCHANT_PAYABLE.getId();
        UUID creditAccount = positive ? ChartOfAccounts.MERCHANT_PAYABLE.getId() : ChartOfAccounts.MERCHANT_RECEIVABLE.getId();
        ledgerRepository.saveAll(List.of(
            new LedgerEntryEntity(transactionId, debitAccount, Direction.DEBIT, amount, currency, paymentId, memo),
            new LedgerEntryEntity(transactionId, creditAccount, Direction.CREDIT, amount, currency, paymentId, memo)
        ));
    }

    @Transactional
    public void recordChargebackOpened(UUID paymentId, long amount, String currency) {
        UUID transactionId = UUID.randomUUID();
        ledgerRepository.saveAll(List.of(
            new LedgerEntryEntity(transactionId, ChartOfAccounts.CHARGEBACK_RESERVE.getId(), Direction.DEBIT, amount, currency, paymentId, "Chargeback opened"),
            new LedgerEntryEntity(transactionId, ChartOfAccounts.MERCHANT_RECEIVABLE.getId(), Direction.CREDIT, amount, currency, paymentId, "Chargeback opened")
        ));
    }

    @Transactional
    public void recordChargebackLost(UUID paymentId, long amount, String currency) {
        UUID transactionId = UUID.randomUUID();
        ledgerRepository.saveAll(List.of(
            new LedgerEntryEntity(transactionId, ChartOfAccounts.MERCHANT_RECEIVABLE.getId(), Direction.DEBIT, amount, currency, paymentId, "Chargeback lost"),
            new LedgerEntryEntity(transactionId, ChartOfAccounts.ACQUIRER_CLEARING.getId(), Direction.CREDIT, amount, currency, paymentId, "Chargeback lost")
        ));
    }

    /** Reverses the reserve held by the OPENED posting. */
    @Transactional
    public void recordChargebackWon(UUID paymentId, long amount, String currency) {
        UUID transactionId = UUID.randomUUID();
        ledgerRepository.saveAll(List.of(
            new LedgerEntryEntity(transactionId, ChartOfAccounts.MERCHANT_RECEIVABLE.getId(), Direction.DEBIT, amount, currency, paymentId, "Chargeback won"),
            new LedgerEntryEntity(transactionId, ChartOfAccounts.CHARGEBACK_RESERVE.getId(), Direction.CREDIT, amount, currency, paymentId, "Chargeback won")
        ));
    }
}
