package com.switchpay.risk;

import java.util.UUID;

public record RiskContext(
    String panFingerprint, String ipAddress, String emailHash, 
    String binCountry, String ipCountry, long amountMinor, 
    UUID merchantId, boolean isNewDevice
) {}
