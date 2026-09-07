package com.switchpay.recon;

/** §13.2: the six discrepancy types the differ is proven against, one test each. */
public enum ReconDiscrepancyType {
    MISSING_AT_ACQUIRER,
    UNKNOWN_AT_GATEWAY,
    AMOUNT_MISMATCH,
    DUPLICATE_AT_ACQUIRER,
    CURRENCY_MISMATCH,
    DATE_OUT_OF_WINDOW
}
