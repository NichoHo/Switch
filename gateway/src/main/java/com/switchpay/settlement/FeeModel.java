package com.switchpay.settlement;

/** §13.1: schemeFee = capture × acquirer.cost_bps + fixed; gatewayFee = capture × merchant.rate_bps + fixed. */
public final class FeeModel {
    private FeeModel() {}

    public record MerchantFees(int rateBps, long fixedFeeMinor) {}
    public record AcquirerCost(int costBps, long costFixedMinor) {}

    public static long bpsOf(long amountMinor, int bps) {
        return Math.round(amountMinor * bps / 10000.0);
    }

    public static long schemeFee(long amountMinor, AcquirerCost cost) {
        return bpsOf(amountMinor, cost.costBps()) + cost.costFixedMinor();
    }

    public static long gatewayFee(long amountMinor, MerchantFees fees) {
        return bpsOf(amountMinor, fees.rateBps()) + fees.fixedFeeMinor();
    }
}
