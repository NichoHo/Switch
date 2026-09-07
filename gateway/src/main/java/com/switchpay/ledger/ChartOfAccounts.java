package com.switchpay.ledger;

import java.util.UUID;

public enum ChartOfAccounts {
    ACQUIRER_CLEARING(UUID.fromString("11111111-1111-1111-1111-111111111111")),
    MERCHANT_RECEIVABLE(UUID.fromString("22222222-2222-2222-2222-222222222222")),
    MERCHANT_PAYABLE(UUID.fromString("33333333-3333-3333-3333-333333333333")),
    GATEWAY_REVENUE(UUID.fromString("44444444-4444-4444-4444-444444444444")),
    SCHEME_FEES(UUID.fromString("55555555-5555-5555-5555-555555555555")),
    REFUNDS_CLEARING(UUID.fromString("66666666-6666-6666-6666-666666666666")),
    CHARGEBACK_RESERVE(UUID.fromString("77777777-7777-7777-7777-777777777777"));

    private final UUID id;

    ChartOfAccounts(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }
}
