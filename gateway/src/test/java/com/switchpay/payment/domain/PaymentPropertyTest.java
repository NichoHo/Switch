package com.switchpay.payment.domain;

import com.switchpay.common.Currency;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaymentPropertyTest {

    private static final long AMOUNT = 5000;

    @Property(tries = 2000)
    void capturedAmount_is_less_than_or_equal_to_amount(@ForAll("operationSequences") Operation[] ops) {
        Payment p = newPayment();
        applySequence(p, ops);
        assertTrue(p.capturedAmountMinor() <= p.amountMinor());
    }

    @Property(tries = 2000)
    void refundedAmount_is_less_than_or_equal_to_capturedAmount(@ForAll("operationSequences") Operation[] ops) {
        Payment p = newPayment();
        applySequence(p, ops);
        assertTrue(p.refundedAmountMinor() <= p.capturedAmountMinor());
    }

    @Property(tries = 2000)
    void eventLog_reconstructs_final_state(@ForAll("operationSequences") Operation[] ops) {
        Payment p = newPayment();
        applySequence(p, ops);

        PaymentState currentState = PaymentState.CREATED;
        for (PaymentEvent event : p.events()) {
            currentState = event.toState();
        }
        assertTrue(p.state() == currentState || (p.events().isEmpty() && p.state() == PaymentState.CREATED));
    }

    @Provide
    Arbitrary<Operation[]> operationSequences() {
        return Arbitraries.of(Operation.values()).array(Operation[].class).ofMinSize(0).ofMaxSize(15);
    }

    private void applySequence(Payment p, Operation[] ops) {
        for (Operation op : ops) {
            try {
                if (p.state().permits(op)) {
                    switch (op) {
                        case RISK_DENY -> p.riskDeny();
                        case RISK_CHALLENGE -> p.riskChallenge();
                        case AUTHORIZE -> p.authorize("acqId", "acqRef", "authCode", Instant.now().plusSeconds(3600));
                        case AUTHENTICATE_OK -> p.authenticateOk();
                        case AUTHENTICATE_FAIL -> p.authenticateFail();
                        case EXPIRE -> p.expire();
                        case CAPTURE -> {
                            long remaining = p.amountMinor() - p.capturedAmountMinor();
                            if (remaining > 0) p.capture(Math.min(1000, remaining));
                        }
                        case VOID -> p.voidPayment();
                        case REFUND -> {
                            long refundable = p.capturedAmountMinor() - p.refundedAmountMinor();
                            if (refundable > 0) p.refund(Math.min(500, refundable));
                        }
                        case PROBE_RESOLVED -> p.probeResolved(true);
                    }
                }
            } catch (Exception e) {
                // Guard clauses (amount checks) may reject even if permits() passed — skip
            }
        }
    }

    private Payment newPayment() {
        return new Payment(UUID.randomUUID(), UUID.randomUUID(), "ref-" + UUID.randomUUID(),
                "tok_test", AMOUNT, Currency.EUR);
    }
}
