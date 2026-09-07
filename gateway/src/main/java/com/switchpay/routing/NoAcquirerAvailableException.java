package com.switchpay.routing;

public class NoAcquirerAvailableException extends RuntimeException {
    public NoAcquirerAvailableException() {
        super("No acquirer available to route the payment");
    }
}
