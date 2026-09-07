package com.switchpay.idempotency;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;

@Component
public class StaleKeyReaper {
    private final IdempotencyRecordRepository repository;

    public StaleKeyReaper(IdempotencyRecordRepository repository) {
        this.repository = repository;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void reapStaleKeys() {
        OffsetDateTime threshold = OffsetDateTime.now().minusMinutes(5);
        repository.deleteStaleInProgressRecords(threshold);
    }
}
