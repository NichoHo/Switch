package com.switchpay.payment.domain;

import java.util.Map;
import java.util.Set;

public enum PaymentState {
    CREATED,
    RISK_DECLINED,
    AUTHENTICATION_PENDING,
    AUTHENTICATION_FAILED,
    AUTHORIZED,
    AUTH_DECLINED,
    AUTH_UNKNOWN,
    PARTIALLY_CAPTURED,
    CAPTURED,
    VOIDED,
    EXPIRED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    private static final Map<PaymentState, Set<Operation>> ALLOWED = Map.ofEntries(
        Map.entry(CREATED, Set.of(Operation.RISK_DENY, Operation.RISK_CHALLENGE, Operation.AUTHORIZE)),
        Map.entry(AUTHENTICATION_PENDING, Set.of(Operation.AUTHENTICATE_OK, Operation.AUTHENTICATE_FAIL, Operation.EXPIRE)),
        Map.entry(AUTH_UNKNOWN, Set.of(Operation.PROBE_RESOLVED)),
        Map.entry(AUTHORIZED, Set.of(Operation.CAPTURE, Operation.VOID, Operation.EXPIRE)),
        Map.entry(PARTIALLY_CAPTURED, Set.of(Operation.CAPTURE, Operation.EXPIRE, Operation.REFUND)),
        Map.entry(CAPTURED, Set.of(Operation.REFUND)),
        Map.entry(PARTIALLY_REFUNDED, Set.of(Operation.REFUND)),
        Map.entry(RISK_DECLINED, Set.of()),
        Map.entry(AUTHENTICATION_FAILED, Set.of()),
        Map.entry(AUTH_DECLINED, Set.of()),
        Map.entry(VOIDED, Set.of()),
        Map.entry(EXPIRED, Set.of()),
        Map.entry(REFUNDED, Set.of())
    );

    public boolean permits(Operation op) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(op);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
