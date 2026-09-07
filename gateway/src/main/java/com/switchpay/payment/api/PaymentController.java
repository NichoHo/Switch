package com.switchpay.payment.api;

import com.switchpay.common.Currency;
import com.switchpay.common.Money;
import com.switchpay.merchant.MerchantContext;
import com.switchpay.payment.PaymentService;
import com.switchpay.payment.domain.Payment;
import com.switchpay.payment.domain.PaymentState;
import com.switchpay.payment.store.PaymentEntity;
import com.switchpay.vault.store.CardTokenEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * §16 — the payment resource.
 *
 * Every mutating call goes through {@code IdempotencyFilter} (§7), which claims the
 * {@code Idempotency-Key} before this controller runs and replays the stored response on a
 * duplicate. Nothing here needs to know that.
 *
 * Every call — mutating or not — goes through {@code MerchantAuthFilter} first, so
 * {@code MerchantContext.require()} below is always a verified identity, never an asserted one
 * (NR-4). Payment-scoped endpoints additionally check that the payment belongs to the caller
 * ({@link #requireOwnership}) — authenticating the caller is not the same as authorizing them to
 * touch any payment ID they happen to guess.
 *
 * NR-14: this used to inject {@code CardTokenRepository} and {@code ThreedsChallengeRepository}
 * directly to assemble its response DTOs. Both reads now live behind {@code PaymentService}
 * ({@code getPaymentWithCard}, {@code findPendingChallengeId}) — the controller's job is HTTP
 * shape, not deciding how to join two tables.
 */
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final String acquirerSimUrl;

    public PaymentController(PaymentService paymentService,
                             @Value("${acquirer.sim.url:http://localhost:8081}") String acquirerSimUrl) {
        this.paymentService = paymentService;
        this.acquirerSimUrl = acquirerSimUrl;
    }

    @PostMapping
    public ResponseEntity<ApiDtos.PaymentResponse> create(
            HttpServletRequest httpRequest, @RequestBody ApiDtos.CreatePaymentRequest request) {

        UUID merchantId = MerchantContext.require(httpRequest);
        Currency currency = Currency.valueOf(request.amount().currency());

        Payment payment = paymentService.createPayment(
                merchantId, request.merchantReference(), request.cardToken(),
                currency, request.amount().minor());

        paymentService.authorize(payment.id(), request.context() == null
                ? com.switchpay.payment.PaymentContext.EMPTY : request.context().toPaymentContext());
        PaymentEntity entity = paymentService.getPayment(payment.id());

        if (PaymentState.RISK_DECLINED.name().equals(entity.getState())) {
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(body(entity, null));
        }

        if (PaymentState.AUTHENTICATION_PENDING.name().equals(entity.getState())) {
            // §11 step 2 says 202; §17 lists authentication_required as 402. Following §17,
            // because the error-code table is what the test suite asserts on — but the two
            // sections disagree and one of them should be corrected.
            ApiDtos.ThreedsAction action = paymentService.findPendingChallengeId(entity.getId())
                    .map(this::redirectFor)
                    .orElse(null);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(body(entity, action));
        }

        if (request.capture() && PaymentState.AUTHORIZED.name().equals(entity.getState())) {
            paymentService.capture(payment.id(), entity.getAmountMinor());
            entity = paymentService.getPayment(payment.id());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(body(entity, null));
    }

    @PostMapping("/{id}/captures")
    public ApiDtos.PaymentResponse capture(HttpServletRequest httpRequest, @PathVariable UUID id,
                                           @RequestBody ApiDtos.AmountRequest request) {
        requireOwnership(id, httpRequest);
        requireSameCurrency(id, request);
        paymentService.capture(id, request.amount().minor());
        return body(paymentService.getPayment(id), null);
    }

    @PostMapping("/{id}/refunds")
    public ApiDtos.PaymentResponse refund(HttpServletRequest httpRequest, @PathVariable UUID id,
                                          @RequestBody ApiDtos.AmountRequest request) {
        requireOwnership(id, httpRequest);
        requireSameCurrency(id, request);
        paymentService.refund(id, request.amount().minor());
        return body(paymentService.getPayment(id), null);
    }

    @PostMapping("/{id}/voids")
    public ApiDtos.PaymentResponse voidPayment(HttpServletRequest httpRequest, @PathVariable UUID id) {
        requireOwnership(id, httpRequest);
        paymentService.voidPayment(id);
        return body(paymentService.getPayment(id), null);
    }

    @GetMapping("/{id}")
    public ApiDtos.PaymentResponse get(HttpServletRequest httpRequest, @PathVariable UUID id) {
        requireOwnership(id, httpRequest);
        return body(paymentService.getPayment(id), null);
    }

    @GetMapping
    public List<ApiDtos.PaymentResponse> list(
            HttpServletRequest httpRequest,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String merchantReference) {

        UUID merchantId = MerchantContext.require(httpRequest);
        return paymentService.listPayments(merchantId, state, merchantReference).stream()
                .map(p -> body(p, null))
                .toList();
    }

    @GetMapping("/{id}/events")
    public List<ApiDtos.EventResponse> events(HttpServletRequest httpRequest, @PathVariable UUID id) {
        requireOwnership(id, httpRequest);
        return paymentService.getEvents(id).stream().map(ApiDtos.EventResponse::of).toList();
    }

    /**
     * A payment ID belonging to another merchant is reported as not found, not as forbidden —
     * confirming a payment exists for a merchant that isn't the caller is itself a leak.
     */
    private void requireOwnership(UUID paymentId, HttpServletRequest httpRequest) {
        UUID merchantId = MerchantContext.require(httpRequest);
        PaymentEntity entity = paymentService.getPayment(paymentId);
        if (!entity.getMerchantId().equals(merchantId)) {
            throw new IllegalArgumentException("payment_not_found");
        }
    }

    /**
     * A capture or refund denominated in another currency is a mistake, not a conversion (§5.1).
     * {@code Money.minus()} is what actually refuses the arithmetic (NR-13) — the difference
     * itself is meaningless here and thrown away; the point is that computing it at all is only
     * legal when both amounts share a currency.
     */
    private void requireSameCurrency(UUID paymentId, ApiDtos.AmountRequest request) {
        PaymentEntity entity = paymentService.getPayment(paymentId);
        Money requested = new Money(request.amount().minor(), Currency.valueOf(request.amount().currency()));
        Money held = new Money(entity.getAmountMinor(), Currency.valueOf(entity.getCurrency()));
        held.minus(requested);
    }

    private ApiDtos.ThreedsAction redirectFor(UUID challengeId) {
        return new ApiDtos.ThreedsAction("redirect", acquirerSimUrl + "/acs/" + challengeId);
    }

    private ApiDtos.PaymentResponse body(PaymentEntity entity, ApiDtos.ThreedsAction action) {
        CardTokenEntity card = paymentService.withCard(entity).card();
        return ApiDtos.PaymentResponse.of(entity, card, action);
    }
}
