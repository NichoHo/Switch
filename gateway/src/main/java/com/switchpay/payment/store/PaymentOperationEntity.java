package com.switchpay.payment.store;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_operation")
public class PaymentOperationEntity {
    @Id
    private UUID id;
    private UUID paymentId;
    private String type;
    private Long amountMinor;
    private String currency;
    private String state;
    private String acquirerId;
    private String acquirerRef;
    private String settlementState = "UNSETTLED";
    private Instant createdAt;

    // Getters and setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(Long amountMinor) { this.amountMinor = amountMinor; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getAcquirerId() { return acquirerId; }
    public void setAcquirerId(String acquirerId) { this.acquirerId = acquirerId; }
    public String getAcquirerRef() { return acquirerRef; }
    public void setAcquirerRef(String acquirerRef) { this.acquirerRef = acquirerRef; }
    public String getSettlementState() { return settlementState; }
    public void setSettlementState(String settlementState) { this.settlementState = settlementState; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
