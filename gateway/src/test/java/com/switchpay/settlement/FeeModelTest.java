package com.switchpay.settlement;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FeeModelTest {

    @Test
    void scheme_fee_is_bps_plus_fixed() {
        long fee = FeeModel.schemeFee(10_000, new FeeModel.AcquirerCost(25, 10));
        assertThat(fee).isEqualTo(10_000 * 25 / 10000 + 10);
    }

    @Test
    void gateway_fee_is_bps_plus_fixed() {
        long fee = FeeModel.gatewayFee(10_000, new FeeModel.MerchantFees(250, 30));
        assertThat(fee).isEqualTo(10_000 * 250 / 10000 + 30);
    }

    @Test
    void zero_amount_is_just_the_fixed_fee() {
        assertThat(FeeModel.schemeFee(0, new FeeModel.AcquirerCost(25, 10))).isEqualTo(10);
    }
}
