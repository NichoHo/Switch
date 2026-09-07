package com.switchpay.acqsim;

import org.springframework.stereotype.Component;

/** Mirrors {@link FaultInjector}'s admin-mutable style, for the six §13.2 discrepancy types. */
@Component
public class SettlementFaultInjector {

    public record FaultConfig(
        int dropRatePercent,             // MISSING_AT_ACQUIRER — row present internally, dropped from file
        int phantomRatePercent,          // UNKNOWN_AT_GATEWAY — row added that the gateway never sent
        int amountMismatchRatePercent,   // AMOUNT_MISMATCH
        int duplicateRatePercent,        // DUPLICATE_AT_ACQUIRER
        int currencyMismatchRatePercent, // CURRENCY_MISMATCH
        int dateOutOfWindowRatePercent   // DATE_OUT_OF_WINDOW
    ) {}

    private volatile FaultConfig config = new FaultConfig(0, 0, 0, 0, 0, 0);

    public FaultConfig getConfig() { return config; }

    public void setConfig(FaultConfig config) { this.config = config; }
}
