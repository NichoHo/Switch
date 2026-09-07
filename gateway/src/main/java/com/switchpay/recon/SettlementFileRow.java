package com.switchpay.recon;

import java.time.LocalDate;

/** One row of what the acquirer *claims* happened (§13.2). */
public record SettlementFileRow(String reference, long amountMinor, String currency, LocalDate businessDate) {}
