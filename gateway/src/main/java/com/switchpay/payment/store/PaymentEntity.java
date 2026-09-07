package com.switchpay.payment.store;

import com.switchpay.common.Currency;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment")
public class PaymentEntity {
    @Id
    private UUID id;
    private UUID merchantId;
    private String merchantReference;
    private String cardToken;
    private String currency;
    private long amountMinor;
    private long capturedAmountMinor;
    private long refundedAmountMinor;
    private String state;
    private String riskDecision;
    private Integer riskScore;
    private String acquirerId;
    private String acquirerReference;
    private String authCode;
    private Instant expiresAt;
    private String disputeState;
    @Version
    private long version;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean liabilityShift;

    // Getters and setters omitted for brevity, but should be generated in a real app
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public String getMerchantReference() { return merchantReference; }
    public void setMerchantReference(String merchantReference) { this.merchantReference = merchantReference; }
    public String getCardToken() { return cardToken; }
    public void setCardToken(String cardToken) { this.cardToken = cardToken; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(long amountMinor) { this.amountMinor = amountMinor; }
    public long getCapturedAmountMinor() { return capturedAmountMinor; }
    public void setCapturedAmountMinor(long capturedAmountMinor) { this.capturedAmountMinor = capturedAmountMinor; }
    public long getRefundedAmountMinor() { return refundedAmountMinor; }
    public void setRefundedAmountMinor(long refundedAmountMinor) { this.refundedAmountMinor = refundedAmountMinor; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getRiskDecision() { return riskDecision; }
    public void setRiskDecision(String riskDecision) { this.riskDecision = riskDecision; }
    public Integer getRiskScore() { return riskScore; }
    public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
    public String getAcquirerId() { return acquirerId; }
    public void setAcquirerId(String acquirerId) { this.acquirerId = acquirerId; }
    public String getAcquirerReference() { return acquirerReference; }
    public void setAcquirerReference(String acquirerReference) { this.acquirerReference = acquirerReference; }
    public String getAuthCode() { return authCode; }
    public void setAuthCode(String authCode) { this.authCode = authCode; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public String getDisputeState() { return disputeState; }
    public void setDisputeState(String disputeState) { this.disputeState = disputeState; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public boolean isLiabilityShift() { return liabilityShift; }
    public void setLiabilityShift(boolean liabilityShift) { this.liabilityShift = liabilityShift; }
}
