package com.switchpay.payment.domain;

public class InvalidTransitionException extends RuntimeException {
    private final String code = "payment_state_invalid";

    public InvalidTransitionException(PaymentState fromState, Operation operation) {
        super("Invalid transition from " + fromState + " using operation " + operation);
    }

    public String getCode() {
        return code;
    }
}
