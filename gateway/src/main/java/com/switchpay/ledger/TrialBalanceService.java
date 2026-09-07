package com.switchpay.ledger;

import com.switchpay.ledger.store.LedgerRepository;
import org.springframework.stereotype.Service;

@Service
public class TrialBalanceService {

    private final LedgerRepository ledgerRepository;

    public TrialBalanceService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    public long getTrialBalance(String currency) {
        return ledgerRepository.calculateTrialBalance(currency);
    }
}
