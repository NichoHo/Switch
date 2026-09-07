package com.switchpay.common;

public record Money(long minor, Currency currency) {
    public Money plus(Money o)  { requireSame(o); return new Money(minor + o.minor, currency); }
    public Money minus(Money o) { requireSame(o); return new Money(minor - o.minor, currency); }
    private void requireSame(Money o) {
        if (!currency.equals(o.currency)) throw new CurrencyMismatchException(currency, o.currency);
    }
}
