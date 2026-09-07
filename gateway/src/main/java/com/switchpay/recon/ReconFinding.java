package com.switchpay.recon;

import java.util.UUID;

public record ReconFinding(
    ReconDiscrepancyType type, String reference, UUID paymentId, String acquirerId,
    Long internalAmountMinor, Long fileAmountMinor, String internalCurrency, String fileCurrency, String detail
) {}
