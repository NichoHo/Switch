package com.switchpay.dispute;

import java.util.Map;
import java.util.Set;

/**
 * §14 — a separate aggregate with its own FSM, kept out of PaymentState for the reason
 * given in §6.3: the payment lifecycle and the dispute lifecycle run on different clocks.
 *
 * OPENED ──> EVIDENCE_SUBMITTED ──> WON | LOST
 *   └──────────────────────────────> LOST   (deadline passed, no evidence)
 */
public enum DisputeState {
    OPENED,
    EVIDENCE_SUBMITTED,
    WON,
    LOST;

    private static final Map<DisputeState, Set<DisputeOperation>> ALLOWED = Map.of(
        OPENED, Set.of(DisputeOperation.SUBMIT_EVIDENCE, DisputeOperation.EXPIRE),
        EVIDENCE_SUBMITTED, Set.of(DisputeOperation.WIN, DisputeOperation.LOSE),
        WON, Set.of(),
        LOST, Set.of()
    );

    public boolean permits(DisputeOperation op) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(op);
    }

    public boolean isTerminal() {
        return ALLOWED.getOrDefault(this, Set.of()).isEmpty();
    }
}
