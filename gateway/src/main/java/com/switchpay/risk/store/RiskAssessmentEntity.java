package com.switchpay.risk.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "risk_assessment")           // insert-only
public class RiskAssessmentEntity {
    @Id
    private UUID id;
    private UUID paymentId;
    private UUID merchantId;
    private String panFingerprint;
    private String ipAddress;
    private String emailHash;
    private String deviceFingerprint;
    private String binCountry;
    private String ipCountry;
    private boolean isNewDevice;
    private long amountMinor;
    private int score;
    private String decision;
    private int denyThreshold;
    private int challengeThreshold;
    private String rulesetVersion;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String breakdown;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }
    public String getPanFingerprint() { return panFingerprint; }
    public void setPanFingerprint(String panFingerprint) { this.panFingerprint = panFingerprint; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public String getEmailHash() { return emailHash; }
    public void setEmailHash(String emailHash) { this.emailHash = emailHash; }
    public String getDeviceFingerprint() { return deviceFingerprint; }
    public void setDeviceFingerprint(String deviceFingerprint) { this.deviceFingerprint = deviceFingerprint; }
    public String getBinCountry() { return binCountry; }
    public void setBinCountry(String binCountry) { this.binCountry = binCountry; }
    public String getIpCountry() { return ipCountry; }
    public void setIpCountry(String ipCountry) { this.ipCountry = ipCountry; }
    public boolean isNewDevice() { return isNewDevice; }
    public void setNewDevice(boolean newDevice) { isNewDevice = newDevice; }
    public long getAmountMinor() { return amountMinor; }
    public void setAmountMinor(long amountMinor) { this.amountMinor = amountMinor; }
    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public int getDenyThreshold() { return denyThreshold; }
    public void setDenyThreshold(int denyThreshold) { this.denyThreshold = denyThreshold; }
    public int getChallengeThreshold() { return challengeThreshold; }
    public void setChallengeThreshold(int challengeThreshold) { this.challengeThreshold = challengeThreshold; }
    public String getRulesetVersion() { return rulesetVersion; }
    public void setRulesetVersion(String rulesetVersion) { this.rulesetVersion = rulesetVersion; }
    public String getBreakdown() { return breakdown; }
    public void setBreakdown(String breakdown) { this.breakdown = breakdown; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
