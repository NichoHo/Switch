package com.switchpay.recon.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recon_exception")
public class ReconExceptionEntity {
    @Id
    private UUID id;
    private LocalDate businessDate;
    private String acquirerId;
    private String type;
    private String severity;
    private String reference;
    private Long internalAmountMinor;
    private Long fileAmountMinor;
    private String internalCurrency;
    private String fileCurrency;
    private String detail;
    private Instant resolvedAt;
    private String resolvedBy;
    private String resolutionNote;
    private Instant createdAt;

    protected ReconExceptionEntity() {}

    public ReconExceptionEntity(UUID id, LocalDate businessDate, String acquirerId, String type, String severity,
                                 String reference, Long internalAmountMinor, Long fileAmountMinor,
                                 String internalCurrency, String fileCurrency, String detail) {
        this.id = id;
        this.businessDate = businessDate;
        this.acquirerId = acquirerId;
        this.type = type;
        this.severity = severity;
        this.reference = reference;
        this.internalAmountMinor = internalAmountMinor;
        this.fileAmountMinor = fileAmountMinor;
        this.internalCurrency = internalCurrency;
        this.fileCurrency = fileCurrency;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public LocalDate getBusinessDate() { return businessDate; }
    public String getAcquirerId() { return acquirerId; }
    public String getType() { return type; }
    public String getSeverity() { return severity; }
    public String getReference() { return reference; }
    public Long getInternalAmountMinor() { return internalAmountMinor; }
    public Long getFileAmountMinor() { return fileAmountMinor; }
    public String getInternalCurrency() { return internalCurrency; }
    public String getFileCurrency() { return fileCurrency; }
    public String getDetail() { return detail; }
    public Instant getResolvedAt() { return resolvedAt; }
    public String getResolvedBy() { return resolvedBy; }
    public String getResolutionNote() { return resolutionNote; }
    public Instant getCreatedAt() { return createdAt; }

    public void resolve(String resolvedBy, String note) {
        this.resolvedAt = Instant.now();
        this.resolvedBy = resolvedBy;
        this.resolutionNote = note;
    }
}
