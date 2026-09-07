package com.switchpay.dispute;

import com.switchpay.dispute.store.DisputeEntity;
import com.switchpay.dispute.store.DisputeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class DisputeExpiryJob {

    private static final Logger logger = LoggerFactory.getLogger(DisputeExpiryJob.class);

    private final DisputeRepository disputeRepository;
    private final DisputeService disputeService;

    public DisputeExpiryJob(DisputeRepository disputeRepository, DisputeService disputeService) {
        this.disputeRepository = disputeRepository;
        this.disputeService = disputeService;
    }

    @Scheduled(fixedDelay = 3_600_000)  // hourly: dispute deadlines run in days, not seconds
    public void expireOverdueDisputes() {
        List<DisputeEntity> overdue = disputeRepository.findByStateAndEvidenceDueAtBefore(
            DisputeState.OPENED.name(), Instant.now());
        for (DisputeEntity dispute : overdue) {
            try {
                disputeService.expire(dispute.getId());
            } catch (Exception e) {
                logger.error("Failed to expire dispute {}", dispute.getId(), e);
            }
        }
    }
}
