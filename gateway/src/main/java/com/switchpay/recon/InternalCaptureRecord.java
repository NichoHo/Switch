package com.switchpay.recon;

import java.time.LocalDate;
import java.util.UUID;

/** The gateway's own view of a capture, as recorded on the payment_operation row. */
public record InternalCaptureRecord(
    String reference, long amountMinor, String currency, LocalDate businessDate,
    UUID paymentId, String acquirerId
) {}
