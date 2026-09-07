package com.switchpay.merchant;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

/** Reads the merchant identity {@link MerchantAuthFilter} already verified for this request. */
public final class MerchantContext {
    private MerchantContext() {}

    public static UUID require(HttpServletRequest request) {
        Object merchantId = request.getAttribute(MerchantAuthFilter.MERCHANT_ID_ATTRIBUTE);
        if (merchantId == null) {
            // Reachable only if a /v1 route is called without going through the filter chain
            // (a misconfigured test, for instance) — MerchantAuthFilter rejects everything else.
            throw new IllegalArgumentException("merchant_id_required");
        }
        return (UUID) merchantId;
    }
}
