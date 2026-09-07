package com.switchpay.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class OutboxPollerJob {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPollerJob.class);

    private final OutboxRepository outboxRepository;
    private final WebhookClient webhookClient;
    private final JdbcTemplate jdbcTemplate;

    public OutboxPollerJob(OutboxRepository outboxRepository, WebhookClient webhookClient, JdbcTemplate jdbcTemplate) {
        this.outboxRepository = outboxRepository;
        this.webhookClient = webhookClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEventEntity> pendingEvents = outboxRepository.findPendingEventsForProcessing();
        
        for (OutboxEventEntity event : pendingEvents) {
            try {
                // Fetch merchant webhook info
                List<Map<String, Object>> results = jdbcTemplate.queryForList(
                        "SELECT webhook_url, webhook_secret FROM merchant WHERE id = ?",
                        event.getMerchantId());
                
                if (results.isEmpty() || results.get(0).get("webhook_url") == null) {
                    // No webhook configured, mark as delivered or dead?
                    // Safe to mark as dead or delivered. We'll mark DEAD.
                    event.setState("DEAD");
                    event.setLastError("No webhook URL configured");
                    outboxRepository.save(event);
                    continue;
                }
                
                String webhookUrl = (String) results.get(0).get("webhook_url");
                String webhookSecret = (String) results.get(0).get("webhook_secret");
                
                webhookClient.sendWebhook(event, webhookUrl, webhookSecret);
                
                event.setState("DELIVERED");
                outboxRepository.save(event);
                
            } catch (Exception e) {
                logger.error("Failed to deliver outbox event {}", event.getId(), e);
                int attempts = event.getAttempts() + 1;
                event.setAttempts(attempts);
                event.setLastError(e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                
                if (attempts >= 12) {
                    event.setState("DEAD");
                } else {
                    long backoffSeconds = Math.min(3600, (long) Math.pow(2, attempts));
                    event.setNextAttemptAt(Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS));
                }
                outboxRepository.save(event);
            }
        }
    }
}
