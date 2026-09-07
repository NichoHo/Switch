package com.switchpay.dispute;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

public class DisputeFsmTest {

    @TestFactory
    Stream<DynamicTest> every_state_operation_pair_behaves_per_the_table() {
        return Arrays.stream(DisputeState.values())
            .flatMap(state -> Arrays.stream(DisputeOperation.values())
                .map(op -> dynamicTest(state + " x " + op, () -> {
                    Dispute d = fixtureIn(state);
                    if (state.permits(op)) {
                        assertDoesNotThrow(() -> apply(d, op));
                    } else {
                        assertThrows(InvalidDisputeTransitionException.class, () -> apply(d, op));
                    }
                })));
    }

    @Test
    void full_win_path() {
        Dispute d = newDispute();
        d.submitEvidence();
        d.win();
        assertEquals(DisputeState.WON, d.state());
        assertTrue(d.state().isTerminal());
    }

    @Test
    void full_lose_path() {
        Dispute d = newDispute();
        d.submitEvidence();
        d.lose();
        assertEquals(DisputeState.LOST, d.state());
    }

    @Test
    void deadline_with_no_evidence_goes_straight_to_lost() {
        Dispute d = newDispute();
        d.expire();
        assertEquals(DisputeState.LOST, d.state());
    }

    private Dispute newDispute() {
        return Dispute.open(UUID.randomUUID(), UUID.randomUUID(), "fraud", 1000L, "EUR", Instant.now().plusSeconds(3600));
    }

    private Dispute fixtureIn(DisputeState state) {
        Dispute d = newDispute();
        switch (state) {
            case OPENED -> { }
            case EVIDENCE_SUBMITTED -> d.submitEvidence();
            case WON -> { d.submitEvidence(); d.win(); }
            case LOST -> d.expire();
        }
        return d;
    }

    private void apply(Dispute d, DisputeOperation op) {
        switch (op) {
            case SUBMIT_EVIDENCE -> d.submitEvidence();
            case WIN -> d.win();
            case LOSE -> d.lose();
            case EXPIRE -> d.expire();
        }
    }
}
