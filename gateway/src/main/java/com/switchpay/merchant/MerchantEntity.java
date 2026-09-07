package com.switchpay.merchant;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchant")
public class MerchantEntity {
    @Id
    private UUID id;
    private String name;
    private String apiKeyHash;
    private String webhookSecret;
    private String webhookUrl;
    private int denyThreshold;
    private int challengeThreshold;
    private int rateBps;
    private long fixedFeeMinor;
    private Instant createdAt;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getApiKeyHash() { return apiKeyHash; }
    public void setApiKeyHash(String apiKeyHash) { this.apiKeyHash = apiKeyHash; }
    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public int getDenyThreshold() { return denyThreshold; }
    public void setDenyThreshold(int denyThreshold) { this.denyThreshold = denyThreshold; }
    public int getChallengeThreshold() { return challengeThreshold; }
    public void setChallengeThreshold(int challengeThreshold) { this.challengeThreshold = challengeThreshold; }
    public int getRateBps() { return rateBps; }
    public void setRateBps(int rateBps) { this.rateBps = rateBps; }
    public long getFixedFeeMinor() { return fixedFeeMinor; }
    public void setFixedFeeMinor(long fixedFeeMinor) { this.fixedFeeMinor = fixedFeeMinor; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
