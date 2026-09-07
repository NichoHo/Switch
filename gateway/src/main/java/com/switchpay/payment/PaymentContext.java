package com.switchpay.payment;

/**
 * The optional {@code context} object from the §16 request example — everything the risk engine
 * needs that isn't derivable from the card token or the amount. {@code ipCountry} has no real
 * geo-IP resolution behind it in this project (§24 names full fraud tooling as out of scope); a
 * caller that has already resolved it may supply it, and {@link com.switchpay.risk.rules.BinCountryMismatchRule}
 * simply doesn't fire when it's absent rather than guessing.
 */
public record PaymentContext(String ip, String emailHash, String deviceFingerprint, String ipCountry) {
    public static final PaymentContext EMPTY = new PaymentContext(null, null, null, null);
}
