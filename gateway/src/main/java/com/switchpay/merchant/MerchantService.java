package com.switchpay.merchant;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public boolean exists(UUID id) {
        return merchantRepository.existsById(id);
    }

    public MerchantEntity save(MerchantEntity merchant) {
        return merchantRepository.save(merchant);
    }
}
