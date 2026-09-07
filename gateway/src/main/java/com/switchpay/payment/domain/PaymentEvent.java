package com.switchpay.payment.domain;

public record PaymentEvent(PaymentState fromState, PaymentState toState, Operation operation, String actor, String reasonCode) {}
