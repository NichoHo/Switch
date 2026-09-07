package com.switchpay.payment.api;

import com.switchpay.payment.store.PaymentEntity;
import com.switchpay.payment.store.PaymentEventEntity;
import com.switchpay.vault.store.CardTokenEntity;

import java.time.Instant;

/** Wire shapes for §16. Money always travels as {minor, currency} — never a bare number. */
final class ApiDtos {
    private ApiDtos() {}

    record MoneyDto(long minor, String currency) {}

    record TokenizeRequest(String pan, short expMonth, short expYear) {}

    record TokenResponse(String token, String brand, String last4, String issuerCountry,
                         short expMonth, short expYear) {
        static TokenResponse of(CardTokenEntity e) {
            return new TokenResponse(e.getToken(), e.getBrand(), e.getLast4(),
                    e.getIssuerCountry(), e.getExpMonth(), e.getExpYear());
        }
    }

    /** §16's request example: {"ip":..., "emailHash":..., "deviceFingerprint":...}. All optional. */
    record ContextDto(String ip, String emailHash, String deviceFingerprint, String ipCountry) {
        com.switchpay.payment.PaymentContext toPaymentContext() {
            return new com.switchpay.payment.PaymentContext(ip, emailHash, deviceFingerprint, ipCountry);
        }
    }

    record CreatePaymentRequest(String merchantReference, String cardToken, MoneyDto amount,
                                boolean capture, ContextDto context) {}

    record AmountRequest(MoneyDto amount) {}

    record AcquirerDto(String id, String reference) {}

    record RiskDto(Integer score, String decision) {}

    record CardDto(String brand, String last4, String issuerCountry) {}

    record ThreedsAction(String type, String url) {}

    record PaymentResponse(
            String id, String state,
            MoneyDto amount, MoneyDto capturedAmount, MoneyDto refundedAmount,
            AcquirerDto acquirer, RiskDto risk, String authCode, Instant expiresAt,
            boolean liabilityShift, String disputeState, CardDto card, ThreedsAction action) {

        static PaymentResponse of(PaymentEntity p, CardTokenEntity card, ThreedsAction action) {
            return new PaymentResponse(
                    p.getId().toString(), p.getState(),
                    new MoneyDto(p.getAmountMinor(), p.getCurrency()),
                    new MoneyDto(p.getCapturedAmountMinor(), p.getCurrency()),
                    new MoneyDto(p.getRefundedAmountMinor(), p.getCurrency()),
                    new AcquirerDto(p.getAcquirerId(), p.getAcquirerReference()),
                    new RiskDto(p.getRiskScore(), p.getRiskDecision()),
                    p.getAuthCode(), p.getExpiresAt(), p.isLiabilityShift(), p.getDisputeState(),
                    card == null ? null : new CardDto(card.getBrand(), card.getLast4(), card.getIssuerCountry()),
                    action);
        }
    }

    record EventResponse(int seq, String fromState, String toState, String actor,
                         String reasonCode, Instant createdAt) {
        static EventResponse of(PaymentEventEntity e) {
            return new EventResponse(e.getSeq(), e.getFromState(), e.getToState(),
                    e.getActor(), e.getReasonCode(), e.getCreatedAt());
        }
    }
}
