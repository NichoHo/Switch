package com.switchpay.vault.store;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CardTokenRepository extends JpaRepository<CardTokenEntity, String> {
    Optional<CardTokenEntity> findByTokenAndMerchantId(String token, UUID merchantId);
}
