package com.switchpay.recon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;

@Component
public class ReconJob {

    private static final Logger logger = LoggerFactory.getLogger(ReconJob.class);

    private final ReconService reconService;

    public ReconJob(ReconService reconService) {
        this.reconService = reconService;
    }

    @Scheduled(cron = "0 30 2 * * *")  // after the settlement batch (§13.1 runs at 02:00)
    public void runNightly() {
        LocalDate businessDate = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        try {
            var exceptions = reconService.reconcileDate(businessDate);
            logger.info("Reconciliation for {} produced {} exception(s)", businessDate, exceptions.size());
        } catch (Exception e) {
            logger.error("Reconciliation failed for {}", businessDate, e);
        }
    }
}
