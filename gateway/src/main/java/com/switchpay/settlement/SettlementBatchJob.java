package com.switchpay.settlement;

import com.switchpay.payment.store.PaymentOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Nightly settlement batch (§13.1). Idempotent by (merchant, currency, acquirer, business_date):
 * a re-run only ever finds unsettled captures, and a group with none produces no batch row:
 * the unique index on settlement_batch is the backstop, not the primary mechanism.
 *
 * Enumerates groups only; {@link SettlementGroupSettler} does the actual (transactional) work of
 * settling one; see its javadoc for why that split matters (NR-15).
 */
@Component
public class SettlementBatchJob {

    private static final Logger logger = LoggerFactory.getLogger(SettlementBatchJob.class);

    private final PaymentOperationRepository operationRepository;
    private final SettlementGroupSettler groupSettler;

    public SettlementBatchJob(PaymentOperationRepository operationRepository, SettlementGroupSettler groupSettler) {
        this.operationRepository = operationRepository;
        this.groupSettler = groupSettler;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void runNightly() {
        runForDate(LocalDate.now(ZoneOffset.UTC).minusDays(1));
    }

    public void runForDate(LocalDate businessDate) {
        // a missed night's run isn't lost: the next run's cutoff still covers every
        // capture that's still UNSETTLED, however old, and stamps it with the businessDate it
        // actually ran for. That's a deliberate choice (catch-up over strict day-bucketing), not
        // an oversight: a system that reliably runs nightly never notices the difference.
        Instant cutoff = businessDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        // one query to enumerate groups, N to settle them. Fine at demo volume;
        // batch the settle-group work in one SQL pass if this ever runs over millions of captures.
        List<Object[]> groups = operationRepository.findUnsettledGroups(cutoff);
        for (Object[] group : groups) {
            UUID merchantId = (UUID) group[0];
            String currency = (String) group[1];
            String acquirerId = (String) group[2];
            try {
                groupSettler.settle(merchantId, currency, acquirerId, businessDate, cutoff);
            } catch (Exception e) {
                logger.error("Settlement failed for merchant={} currency={} acquirer={} date={}",
                    merchantId, currency, acquirerId, businessDate, e);
            }
        }
    }
}
