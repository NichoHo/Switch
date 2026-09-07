package com.switchpay.settlement;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlement_item")           // insert-only
public class SettlementItemEntity {
    @Id
    private UUID id;
    private UUID batchId;
    private UUID paymentOperationId;
    private UUID paymentId;
    private long amountMinor;
    private String currency;
    private Instant createdAt;

    protected SettlementItemEntity() {}

    public SettlementItemEntity(UUID id, UUID batchId, UUID paymentOperationId, UUID paymentId, long amountMinor, String currency) {
        this.id = id;
        this.batchId = batchId;
        this.paymentOperationId = paymentOperationId;
        this.paymentId = paymentId;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getBatchId() { return batchId; }
    public UUID getPaymentOperationId() { return paymentOperationId; }
    public UUID getPaymentId() { return paymentId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Instant getCreatedAt() { return createdAt; }
}
