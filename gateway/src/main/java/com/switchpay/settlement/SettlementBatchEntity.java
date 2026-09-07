package com.switchpay.settlement;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "settlement_batch")
public class SettlementBatchEntity {
    @Id
    private UUID id;
    private UUID merchantId;
    private String currency;
    private String acquirerId;
    private LocalDate businessDate;
    private long grossMinor;
    private long refundsMinor;
    private long schemeFeeMinor;
    private long gatewayFeeMinor;
    private long netMinor;
    private Instant createdAt;

    protected SettlementBatchEntity() {}

    public SettlementBatchEntity(UUID id, UUID merchantId, String currency, String acquirerId, LocalDate businessDate,
                                  long grossMinor, long refundsMinor, long schemeFeeMinor, long gatewayFeeMinor, long netMinor) {
        this.id = id;
        this.merchantId = merchantId;
        this.currency = currency;
        this.acquirerId = acquirerId;
        this.businessDate = businessDate;
        this.grossMinor = grossMinor;
        this.refundsMinor = refundsMinor;
        this.schemeFeeMinor = schemeFeeMinor;
        this.gatewayFeeMinor = gatewayFeeMinor;
        this.netMinor = netMinor;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getCurrency() { return currency; }
    public String getAcquirerId() { return acquirerId; }
    public LocalDate getBusinessDate() { return businessDate; }
    public long getGrossMinor() { return grossMinor; }
    public long getRefundsMinor() { return refundsMinor; }
    public long getSchemeFeeMinor() { return schemeFeeMinor; }
    public long getGatewayFeeMinor() { return gatewayFeeMinor; }
    public long getNetMinor() { return netMinor; }
    public Instant getCreatedAt() { return createdAt; }
}
