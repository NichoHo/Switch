package com.switchpay.contracts;

public record AuthorizationRequest(
    String idempotencyKey,
    String bin,
    String last4,
    String brand,
    String fundingType,
    String issuerCountry,
    String currency,
    long amountMinor,
    String merchantId,
    String merchantReference
) {}
