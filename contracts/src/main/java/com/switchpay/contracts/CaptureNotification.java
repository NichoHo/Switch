package com.switchpay.contracts;

public record CaptureNotification(
    String reference,       // the gateway's payment_operation id: the recon matching key
    long amountMinor,
    String currency,
    String businessDate      // ISO-8601 date the capture is attributed to, e.g. "2026-08-07"
) {}
