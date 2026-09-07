package com.switchpay.payment.domain;

import com.switchpay.common.Currency;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class PaymentStateTransitionTest {

    private static final long AMOUNT = 5000;

    @TestFactory
    Stream<DynamicTest> every_state_operation_pair_behaves_per_the_table() {
        return Arrays.stream(PaymentState.values())
            .flatMap(state -> Arrays.stream(Operation.values())
                .map(op -> dynamicTest(state + " × " + op, () -> {
                    Payment p = fixtureIn(state);
                    if (state.permits(op)) {
                        assertDoesNotThrow(() -> applyOp(p, op));
                    } else {
                        InvalidTransitionException ex = assertThrows(
                            InvalidTransitionException.class, () -> applyOp(p, op));
                        assertEquals("payment_state_invalid", ex.getCode());
                    }
                })));
    }

    /**
     * Creates a Payment in the given state by driving it through valid transitions.
     */
    private Payment fixtureIn(PaymentState state) {
        Payment p = newPayment();
        switch (state) {
            case CREATED:
                break;
            case RISK_DECLINED:
                p.riskDeny();
                break;
            case AUTHENTICATION_PENDING:
                p.riskChallenge();
                break;
            case AUTHENTICATION_FAILED:
                p.riskChallenge();
                p.authenticateFail();
                break;
            case AUTHORIZED:
                p.authorize("acq", "acqRef", "authCode", futureExpiry());
                break;
            case AUTH_DECLINED:
                p.authDecline();
                break;
            case AUTH_UNKNOWN:
                p.authUnknown();
                break;
            case PARTIALLY_CAPTURED:
                p.authorize("acq", "acqRef", "authCode", futureExpiry());
                p.capture(3000);
                break;
            case CAPTURED:
                p.authorize("acq", "acqRef", "authCode", futureExpiry());
                p.capture(AMOUNT);
                break;
            case VOIDED:
                p.authorize("acq", "acqRef", "authCode", futureExpiry());
                p.voidPayment();
                break;
            case EXPIRED:
                p.authorize("acq", "acqRef", "authCode", futureExpiry());
                p.expire();
                break;
            case PARTIALLY_REFUNDED:
                p.authorize("acq", "acqRef", "authCode", futureExpiry());
                p.capture(AMOUNT);
                p.refund(3000);
                break;
            case REFUNDED:
                p.authorize("acq", "acqRef", "authCode", futureExpiry());
                p.capture(AMOUNT);
                p.refund(AMOUNT);
                break;
        }
        assertEquals(state, p.state(), "fixtureIn(" + state + ") did not produce expected state");
        return p;
    }

    private void applyOp(Payment p, Operation op) {
        switch (op) {
            case RISK_DENY -> p.riskDeny();
            case RISK_CHALLENGE -> p.riskChallenge();
            case AUTHORIZE -> p.authorize("acqId", "acqRef", "authCode", futureExpiry());
            case AUTHENTICATE_OK -> p.authenticateOk();
            case AUTHENTICATE_FAIL -> p.authenticateFail();
            case EXPIRE -> p.expire();
            case CAPTURE -> p.capture(Math.min(1000, p.amountMinor() - p.capturedAmountMinor()));
            case VOID -> p.voidPayment();
            case REFUND -> p.refund(Math.min(500, p.capturedAmountMinor() - p.refundedAmountMinor()));
            case PROBE_RESOLVED -> p.probeResolved(true);
        }
    }

    // --- Named scenario tests from the blueprint ---

    @Test
    void authorize_capture_full_then_capture_again_rejected() {
        Payment p = fixtureIn(PaymentState.AUTHORIZED);
        p.capture(AMOUNT);
        assertEquals(PaymentState.CAPTURED, p.state());
        // CAPTURED does not permit CAPTURE
        assertThrows(InvalidTransitionException.class, () -> p.capture(1));
    }

    @Test
    void authorize_void_then_capture_rejected() {
        Payment p = fixtureIn(PaymentState.AUTHORIZED);
        p.voidPayment();
        assertEquals(PaymentState.VOIDED, p.state());
        assertThrows(InvalidTransitionException.class, () -> p.capture(AMOUNT));
    }

    @Test
    void authorize_capture_then_void_rejected() {
        Payment p = fixtureIn(PaymentState.AUTHORIZED);
        p.capture(AMOUNT);
        assertEquals(PaymentState.CAPTURED, p.state());
        // VOID is not permitted from CAPTURED
        assertThrows(InvalidTransitionException.class, p::voidPayment);
    }

    @Test
    void authorize_refund_rejected_nothing_captured() {
        Payment p = fixtureIn(PaymentState.AUTHORIZED);
        // REFUND is not permitted from AUTHORIZED
        assertThrows(InvalidTransitionException.class, () -> p.refund(1000));
    }

    @Test
    void capture_3000_of_5000_then_capture_2500_exceeds_authorized() {
        Payment p = fixtureIn(PaymentState.AUTHORIZED);
        p.capture(3000);
        assertEquals(PaymentState.PARTIALLY_CAPTURED, p.state());
        // remaining is 2000, capturing 2500 should fail
        assertThrows(IllegalArgumentException.class, () -> p.capture(2500));
    }

    @Test
    void capture_partial_then_expire_becomes_captured() {
        Payment p = fixtureIn(PaymentState.AUTHORIZED);
        p.capture(3000);
        assertEquals(PaymentState.PARTIALLY_CAPTURED, p.state());
        p.expire();
        assertEquals(PaymentState.CAPTURED, p.state());
        assertEquals(3000, p.capturedAmountMinor());
    }

    @Test
    void refund_1000_refund_1000_refund_1500_of_3000_third_rejected() {
        Payment p = fixtureIn(PaymentState.AUTHORIZED);
        p.capture(3000);
        p.refund(1000);
        assertEquals(PaymentState.PARTIALLY_REFUNDED, p.state());
        p.refund(1000);
        assertEquals(PaymentState.PARTIALLY_REFUNDED, p.state());
        // refundable = 3000 - 2000 = 1000, but trying to refund 1500
        assertThrows(IllegalArgumentException.class, () -> p.refund(1500));
    }

    // --- Helpers ---

    private Payment newPayment() {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                "tok_test", AMOUNT, Currency.EUR);
    }

    private Instant futureExpiry() {
        return Instant.now().plus(1, ChronoUnit.HOURS);
    }
}
