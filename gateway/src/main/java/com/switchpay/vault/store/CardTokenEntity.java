package com.switchpay.vault.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "card_token")
public class CardTokenEntity {

    @Id
    @Column(name = "token")
    private String token;

    @Column(name = "merchant_id", nullable = false)
    private UUID merchantId;

    @Column(name = "pan_ciphertext", nullable = false)
    private byte[] panCiphertext;

    @Column(name = "key_version", nullable = false)
    private short keyVersion;

    @Column(name = "pan_fingerprint", nullable = false)
    private byte[] panFingerprint;

    @Column(name = "bin", nullable = false, length = 8)
    private String bin;

    @Column(name = "last4", nullable = false, length = 4)
    private String last4;

    @Column(name = "brand", nullable = false)
    private String brand;

    @Column(name = "funding_type", nullable = false)
    private String fundingType;

    @Column(name = "issuer_country", nullable = false, length = 2)
    private String issuerCountry;

    @Column(name = "exp_month", nullable = false)
    private short expMonth;

    @Column(name = "exp_year", nullable = false)
    private short expYear;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Getters and Setters

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public UUID getMerchantId() { return merchantId; }
    public void setMerchantId(UUID merchantId) { this.merchantId = merchantId; }

    public byte[] getPanCiphertext() { return panCiphertext; }
    public void setPanCiphertext(byte[] panCiphertext) { this.panCiphertext = panCiphertext; }

    public short getKeyVersion() { return keyVersion; }
    public void setKeyVersion(short keyVersion) { this.keyVersion = keyVersion; }

    public byte[] getPanFingerprint() { return panFingerprint; }
    public void setPanFingerprint(byte[] panFingerprint) { this.panFingerprint = panFingerprint; }

    public String getBin() { return bin; }
    public void setBin(String bin) { this.bin = bin; }

    public String getLast4() { return last4; }
    public void setLast4(String last4) { this.last4 = last4; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getFundingType() { return fundingType; }
    public void setFundingType(String fundingType) { this.fundingType = fundingType; }

    public String getIssuerCountry() { return issuerCountry; }
    public void setIssuerCountry(String issuerCountry) { this.issuerCountry = issuerCountry; }

    public short getExpMonth() { return expMonth; }
    public void setExpMonth(short expMonth) { this.expMonth = expMonth; }

    public short getExpYear() { return expYear; }
    public void setExpYear(short expYear) { this.expYear = expYear; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
