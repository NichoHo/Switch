package com.switchpay.risk.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RulesetRepository extends JpaRepository<RulesetEntity, String> {
    Optional<RulesetEntity> findByIsActiveTrue();
}
