package com.switchpay.common;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(Currency c1, Currency c2) {
        super("Currency mismatch: " + c1 + " and " + c2);
    }
}
