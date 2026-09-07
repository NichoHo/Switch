package com.switchpay.recon;

import com.switchpay.payment.store.PaymentOperationEntity;
import com.switchpay.payment.store.PaymentOperationRepository;
import com.switchpay.recon.store.ReconExceptionEntity;
import com.switchpay.recon.store.ReconExceptionRepository;
import com.switchpay.routing.AcquirerClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class ReconService {

    private final PaymentOperationRepository operationRepository;
    private final ReconExceptionRepository exceptionRepository;
    private final AcquirerClient acquirerClient;
    private final String acquirerSimUrl;

    public ReconService(PaymentOperationRepository operationRepository, ReconExceptionRepository exceptionRepository,
                         AcquirerClient acquirerClient, @Value("${acquirer.sim.url:http://localhost:8081}") String acquirerSimUrl,
                         MeterRegistry meterRegistry) {
        this.operationRepository = operationRepository;
        this.exceptionRepository = exceptionRepository;
        this.acquirerClient = acquirerClient;
        this.acquirerSimUrl = acquirerSimUrl;
        for (ReconDiscrepancyType type : ReconDiscrepancyType.values()) {
            meterRegistry.gauge("switch_recon_exceptions_open", Tags.of("type", type.name()),
                exceptionRepository, repo -> repo.countByTypeAndResolvedAtIsNull(type.name()));
        }
    }

    @Transactional
    public List<ReconExceptionEntity> reconcileDate(LocalDate businessDate) {
        Instant start = businessDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = businessDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<InternalCaptureRecord> internal = operationRepository.findCapturesInWindow(start, end).stream()
            .map(op -> toInternalRecord(op, businessDate))
            .toList();

        String csv = acquirerClient.fetchSettlementFile(acquirerSimUrl, businessDate.toString());
        List<SettlementFileRow> fileRows = SettlementFileParser.parse(csv);

        List<ReconFinding> findings = ReconDiffer.diff(internal, fileRows, businessDate);

        return findings.stream()
            .map(f -> exceptionRepository.save(toEntity(f, businessDate)))
            .toList();
    }

    public List<ReconExceptionEntity> openExceptions() {
        return exceptionRepository.findByResolvedAtIsNull();
    }

    @Transactional
    public ReconExceptionEntity resolve(UUID exceptionId, String actor, String note) {
        ReconExceptionEntity exception = exceptionRepository.findById(exceptionId)
            .orElseThrow(() -> new IllegalArgumentException("Recon exception not found"));
        exception.resolve(actor, note);
        return exceptionRepository.save(exception);
    }

    private InternalCaptureRecord toInternalRecord(PaymentOperationEntity op, LocalDate businessDate) {
        return new InternalCaptureRecord(op.getId().toString(), op.getAmountMinor(), op.getCurrency(),
            businessDate, op.getPaymentId(), op.getAcquirerId());
    }

    private ReconExceptionEntity toEntity(ReconFinding finding, LocalDate businessDate) {
        return new ReconExceptionEntity(
            UUID.randomUUID(), businessDate,
            finding.acquirerId() != null ? finding.acquirerId() : "UNKNOWN",
            finding.type().name(), severityOf(finding.type()), finding.reference(),
            finding.internalAmountMinor(), finding.fileAmountMinor(),
            finding.internalCurrency(), finding.fileCurrency(), finding.detail());
    }

    private String severityOf(ReconDiscrepancyType type) {
        return switch (type) {
            case AMOUNT_MISMATCH, CURRENCY_MISMATCH, MISSING_AT_ACQUIRER, UNKNOWN_AT_GATEWAY -> "HIGH";
            case DUPLICATE_AT_ACQUIRER -> "MEDIUM";
            case DATE_OUT_OF_WINDOW -> "LOW";
        };
    }
}
