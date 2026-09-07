package com.switchpay.payment.domain;

public enum Operation {
    RISK_DENY,
    RISK_CHALLENGE,
    AUTHORIZE,
    AUTHENTICATE_OK,
    AUTHENTICATE_FAIL,
    EXPIRE,
    CAPTURE,
    VOID,
    REFUND,
    PROBE_RESOLVED
}
