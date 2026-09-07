package com.switchpay.dispute;

public class InvalidDisputeTransitionException extends RuntimeException {
    public InvalidDisputeTransitionException(DisputeState state, DisputeOperation op) {
        super("Cannot " + op + " a dispute in state " + state);
    }
}
