package com.switchpay.risk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.switchpay.risk.store.RulesetEntity;
import com.switchpay.risk.store.RulesetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

@Service
public class RulesetService {

    private final RulesetRepository repository;
    private final ObjectMapper objectMapper;

    public RulesetService(RulesetRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public ActiveRuleset getActiveRuleset() {
        Optional<RulesetEntity> entity = repository.findByIsActiveTrue();
        if (entity.isEmpty()) {
            // Fallback for tests or before migration v9 is seeded
            return new ActiveRuleset("v1", Collections.emptyMap());
        }
        return parse(entity.get());
    }

    public ActiveRuleset getRuleset(String version) {
        RulesetEntity entity = repository.findById(version)
                .orElseThrow(() -> new IllegalArgumentException("Ruleset version not found: " + version));
        return parse(entity);
    }

    @Transactional
    public void createRuleset(String version, Map<String, RiskRuleMode> rules, boolean makeActive) {
        if (repository.existsById(version)) {
            throw new IllegalArgumentException("Ruleset version already exists: " + version);
        }

        if (makeActive) {
            repository.findByIsActiveTrue().ifPresent(old -> {
                old.setActive(false);
                repository.saveAndFlush(old);
            });
        }

        RulesetEntity entity = new RulesetEntity();
        entity.setVersion(version);
        entity.setActive(makeActive);
        entity.setCreatedAt(Instant.now());
        
        try {
            entity.setRules(objectMapper.writeValueAsString(rules));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ruleset", e);
        }
        
        repository.save(entity);
    }

    @Transactional
    public void activateRuleset(String version) {
        RulesetEntity entity = repository.findById(version)
                .orElseThrow(() -> new IllegalArgumentException("Ruleset version not found: " + version));

        if (entity.isActive()) return;

        repository.findByIsActiveTrue().ifPresent(old -> {
            old.setActive(false);
            repository.saveAndFlush(old);
        });

        entity.setActive(true);
        repository.save(entity);
    }

    private ActiveRuleset parse(RulesetEntity entity) {
        try {
            Map<String, RiskRuleMode> rules = objectMapper.readValue(
                    entity.getRules(), new TypeReference<Map<String, RiskRuleMode>>() {});
            return new ActiveRuleset(entity.getVersion(), rules);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse ruleset JSON", e);
        }
    }

    public record ActiveRuleset(String version, Map<String, RiskRuleMode> rules) {
        public RiskRuleMode getMode(String ruleCode) {
            return rules.getOrDefault(ruleCode, RiskRuleMode.INACTIVE);
        }
    }
}
