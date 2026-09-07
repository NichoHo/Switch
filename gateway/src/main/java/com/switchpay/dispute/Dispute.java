package com.switchpay.dispute;

import java.time.Instant;
import java.util.UUID;

/** Dispute aggregate — pure domain model, mirrors the Payment aggregate's style. */
public class Dispute {
    private final UUID id;
    private final UUID paymentId;
    private final String reasonCode;
    private final long amountMinor;
    private final String currency;
    private final Instant evidenceDueAt;
    private DisputeState state;

    public Dispute(UUID id, UUID paymentId, String reasonCode, long amountMinor, String currency,
                    DisputeState state, Instant evidenceDueAt) {
        this.id = id;
        this.paymentId = paymentId;
        this.reasonCode = reasonCode;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.state = state;
        this.evidenceDueAt = evidenceDueAt;
    }

    public static Dispute open(UUID id, UUID paymentId, String reasonCode, long amountMinor, String currency, Instant evidenceDueAt) {
        return new Dispute(id, paymentId, reasonCode, amountMinor, currency, DisputeState.OPENED, evidenceDueAt);
    }

    private void checkPermits(DisputeOperation op) {
        if (!state.permits(op)) {
            throw new InvalidDisputeTransitionException(state, op);
        }
    }

    public void submitEvidence() {
        checkPermits(DisputeOperation.SUBMIT_EVIDENCE);
        state = DisputeState.EVIDENCE_SUBMITTED;
    }

    public void win() {
        checkPermits(DisputeOperation.WIN);
        state = DisputeState.WON;
    }

    public void lose() {
        checkPermits(DisputeOperation.LOSE);
        state = DisputeState.LOST;
    }

    /** Deadline passed with no evidence submitted. */
    public void expire() {
        checkPermits(DisputeOperation.EXPIRE);
        state = DisputeState.LOST;
    }

    public UUID id() { return id; }
    public UUID paymentId() { return paymentId; }
    public String reasonCode() { return reasonCode; }
    public long amountMinor() { return amountMinor; }
    public String currency() { return currency; }
    public DisputeState state() { return state; }
    public Instant evidenceDueAt() { return evidenceDueAt; }
}
