package com.switchpay.risk.store;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "risk_ruleset")
public class RulesetEntity {
    
    @Id
    private String version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String rules;

    private boolean isActive;

    private Instant createdAt;

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getRules() { return rules; }
    public void setRules(String rules) { this.rules = rules; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
