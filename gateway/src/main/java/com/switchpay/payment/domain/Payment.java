package com.switchpay.payment.domain;

import com.switchpay.common.Currency;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payment aggregate: pure domain model.
 * No Spring, no JPA, no servlet dependencies.
 */
public class Payment {
    private UUID id;
    private UUID merchantId;
    private String merchantReference;
    private String cardToken;
    private Currency currency;
    private long amountMinor;
    private long capturedAmountMinor;
    private long refundedAmountMinor;
    private PaymentState state;
    private Instant expiresAt;
    private long version;
    private boolean liabilityShift;
    private List<PaymentEvent> events = new ArrayList<>();

    public Payment(UUID id, UUID merchantId, String merchantReference, String cardToken, long amountMinor, Currency currency) {
        this.id = id;
        this.merchantId = merchantId;
        this.merchantReference = merchantReference;
        this.cardToken = cardToken;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.capturedAmountMinor = 0;
        this.refundedAmountMinor = 0;
        this.state = PaymentState.CREATED;
        this.liabilityShift = false;
    }

    private Payment(UUID id, UUID merchantId, String merchantReference, String cardToken, long amountMinor,
                     Currency currency, PaymentState state, long capturedAmountMinor, long refundedAmountMinor,
                     Instant expiresAt, long version, boolean liabilityShift) {
        this.id = id;
        this.merchantId = merchantId;
        this.merchantReference = merchantReference;
        this.cardToken = cardToken;
        this.amountMinor = amountMinor;
        this.currency = currency;
        this.state = state;
        this.capturedAmountMinor = capturedAmountMinor;
        this.refundedAmountMinor = refundedAmountMinor;
        this.expiresAt = expiresAt;
        this.version = version;
        this.liabilityShift = liabilityShift;
    }

    /**
     * Rehydrates an aggregate from persisted state (NR-3): no transition validation, because
     * this isn't a domain operation, it's loading one that already happened. The only legitimate
     * caller is the persistence layer ({@code PaymentService.mapToDomain()}); reaching for this
     * instead of the operation methods above bypasses the FSM the same way reflection used to.
     */
    public static Payment reconstitute(UUID id, UUID merchantId, String merchantReference, String cardToken,
                                        long amountMinor, Currency currency, PaymentState state,
                                        long capturedAmountMinor, long refundedAmountMinor, Instant expiresAt,
                                        long version, boolean liabilityShift) {
        return new Payment(id, merchantId, merchantReference, cardToken, amountMinor, currency, state,
                capturedAmountMinor, refundedAmountMinor, expiresAt, version, liabilityShift);
    }

    private void checkPermits(Operation op) {
        if (!state.permits(op)) {
            throw new InvalidTransitionException(state, op);
        }
    }

    private void transition(PaymentState toState, Operation op, String actor, String reasonCode) {
        events.add(new PaymentEvent(this.state, toState, op, actor, reasonCode));
        this.state = toState;
    }

    public void authorize(String acquirerId, String acquirerRef, String authCode, Instant expiresAt) {
        checkPermits(Operation.AUTHORIZE);
        this.expiresAt = expiresAt;
        transition(PaymentState.AUTHORIZED, Operation.AUTHORIZE, "API", null);
    }

    public void authDecline() {
        checkPermits(Operation.AUTHORIZE);
        transition(PaymentState.AUTH_DECLINED, Operation.AUTHORIZE, "API", "acquirer_declined");
    }

    public void authUnknown() {
        checkPermits(Operation.AUTHORIZE);
        transition(PaymentState.AUTH_UNKNOWN, Operation.AUTHORIZE, "API", "read_timeout");
    }

    public void riskDeny() {
        checkPermits(Operation.RISK_DENY);
        transition(PaymentState.RISK_DECLINED, Operation.RISK_DENY, "API", null);
    }

    public void riskChallenge() {
        checkPermits(Operation.RISK_CHALLENGE);
        transition(PaymentState.AUTHENTICATION_PENDING, Operation.RISK_CHALLENGE, "API", null);
    }

    /**
     * 3DS challenge succeeded. Transitions AUTHENTICATION_PENDING → CREATED
     * so the caller can then invoke authorize().
     */
    public void authenticateOk() {
        checkPermits(Operation.AUTHENTICATE_OK);
        this.liabilityShift = true;
        transition(PaymentState.CREATED, Operation.AUTHENTICATE_OK, "API", null);
    }

    public void authenticateFail() {
        checkPermits(Operation.AUTHENTICATE_FAIL);
        transition(PaymentState.AUTHENTICATION_FAILED, Operation.AUTHENTICATE_FAIL, "API", null);
    }

    public void probeResolved(boolean approved) {
        checkPermits(Operation.PROBE_RESOLVED);
        if (approved) {
            transition(PaymentState.AUTHORIZED, Operation.PROBE_RESOLVED, "JOB:probe", null);
        } else {
            transition(PaymentState.AUTH_DECLINED, Operation.PROBE_RESOLVED, "JOB:probe", "acquirer_declined");
        }
    }

    public void capture(long amountToCapture) {
        checkPermits(Operation.CAPTURE);
        long remaining = this.amountMinor - this.capturedAmountMinor;
        if (amountToCapture <= 0 || amountToCapture > remaining) {
            throw new IllegalArgumentException("capture_exceeds_authorized");
        }
        this.capturedAmountMinor += amountToCapture;
        PaymentState toState = (this.capturedAmountMinor == this.amountMinor)
                ? PaymentState.CAPTURED
                : PaymentState.PARTIALLY_CAPTURED;
        transition(toState, Operation.CAPTURE, "API", null);
    }

    public void voidPayment() {
        checkPermits(Operation.VOID);
        if (this.capturedAmountMinor > 0) {
            throw new IllegalStateException("void_after_capture");
        }
        transition(PaymentState.VOIDED, Operation.VOID, "API", null);
    }

    /**
     * Expire handles different transitions depending on current state:
     * - AUTHENTICATION_PENDING → AUTHENTICATION_FAILED (challenge TTL passed)
     * - AUTHORIZED (captured == 0) → EXPIRED
     * - PARTIALLY_CAPTURED → CAPTURED (remaining reservation released; captured stands)
     */
    public void expire() {
        checkPermits(Operation.EXPIRE);
        switch (this.state) {
            case AUTHENTICATION_PENDING:
                transition(PaymentState.AUTHENTICATION_FAILED, Operation.EXPIRE, "JOB:expiry", "challenge_expired");
                break;
            case AUTHORIZED:
                if (this.capturedAmountMinor > 0) {
                    throw new IllegalStateException("Cannot expire with captures from AUTHORIZED");
                }
                transition(PaymentState.EXPIRED, Operation.EXPIRE, "JOB:expiry", null);
                break;
            case PARTIALLY_CAPTURED:
                // Remaining reservation released; captured amount stands
                transition(PaymentState.CAPTURED, Operation.EXPIRE, "JOB:expiry", "reservation_released");
                break;
            default:
                throw new InvalidTransitionException(state, Operation.EXPIRE);
        }
    }

    public void refund(long amountToRefund) {
        checkPermits(Operation.REFUND);
        long refundable = this.capturedAmountMinor - this.refundedAmountMinor;
        if (amountToRefund <= 0 || amountToRefund > refundable) {
            throw new IllegalArgumentException("refund_exceeds_captured");
        }
        this.refundedAmountMinor += amountToRefund;
        PaymentState toState = (this.refundedAmountMinor == this.capturedAmountMinor)
                ? PaymentState.REFUNDED
                : PaymentState.PARTIALLY_REFUNDED;
        transition(toState, Operation.REFUND, "API", null);
    }

    // Convenience getters matching the names tests expect
    public UUID id() { return id; }
    public UUID merchantId() { return merchantId; }
    public String merchantReference() { return merchantReference; }
    public String cardToken() { return cardToken; }
    public Currency currency() { return currency; }
    public long amountMinor() { return amountMinor; }
    public long capturedAmountMinor() { return capturedAmountMinor; }
    public long refundedAmountMinor() { return refundedAmountMinor; }
    public PaymentState state() { return state; }
    public Instant expiresAt() { return expiresAt; }
    public long version() { return version; }
    public void setVersion(long version) { this.version = version; }
    public List<PaymentEvent> events() { return events; }

    // Bean-style getters for JPA mapper compatibility
    public UUID getId() { return id; }
    public UUID getMerchantId() { return merchantId; }
    public String getMerchantReference() { return merchantReference; }
    public String getCardToken() { return cardToken; }
    public Currency getCurrency() { return currency; }
    public long getAmountMinor() { return amountMinor; }
    public long getCapturedAmountMinor() { return capturedAmountMinor; }
    public long getRefundedAmountMinor() { return refundedAmountMinor; }
    public PaymentState getState() { return state; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getVersion() { return version; }
    public boolean isLiabilityShift() { return liabilityShift; }
    public List<PaymentEvent> getEvents() { return events; }
}
