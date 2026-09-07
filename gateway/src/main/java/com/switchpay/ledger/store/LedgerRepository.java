package com.switchpay.ledger.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<LedgerEntryEntity, Long> {

    @Query("SELECT COALESCE(SUM(CASE WHEN e.direction = 'DEBIT' THEN e.amountMinor ELSE -e.amountMinor END), 0) FROM LedgerEntryEntity e WHERE e.currency = :currency")
    long calculateTrialBalance(@Param("currency") String currency);
}
