package com.switchpay.routing;

import com.switchpay.contracts.AuthorizationStatusResponse;
import com.switchpay.payment.PaymentService;
import com.switchpay.payment.store.PaymentEntity;
import com.switchpay.payment.store.PaymentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Component
public class StatusProbeJob {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;
    private final AcquirerClient acquirerClient;
    private final AcquirerDirectory acquirerDirectory;

    public StatusProbeJob(PaymentRepository paymentRepository, PaymentService paymentService, 
                          AcquirerClient acquirerClient, AcquirerDirectory acquirerDirectory) {
        this.paymentRepository = paymentRepository;
        this.paymentService = paymentService;
        this.acquirerClient = acquirerClient;
        this.acquirerDirectory = acquirerDirectory;
    }

    @Scheduled(fixedDelay = 30000)
    public void probeAuthUnknownPayments() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant tenSecondsAgo = Instant.now().minus(10, ChronoUnit.SECONDS);
        
        List<PaymentEntity> unknownPayments = paymentRepository.findAuthUnknown(cutoff);
        
        for (PaymentEntity payment : unknownPayments) {
            if (payment.getCreatedAt().isAfter(tenSecondsAgo)) {
                continue;
            }
            
            String acquirerId = payment.getAcquirerId();
            if (acquirerId == null) {
                continue;
            }
            
            Optional<AcquirerDirectory.AcquirerEntry> entryOpt = acquirerDirectory.findById(acquirerId);
            if (entryOpt.isEmpty()) {
                continue;
            }
            
            try {
                // payment.getId() is used as idempotency key
                AuthorizationStatusResponse response = acquirerClient.statusProbe(entryOpt.get().baseUrl(), payment.getId().toString());
                if (response != null && response.status() != null) {
                    boolean approved = "APPROVED".equals(response.status());
                    paymentService.resolveAuthUnknown(payment.getId(), approved);
                }
            } catch (Exception e) {
                // Ignore and retry next time
            }
        }
    }
}
