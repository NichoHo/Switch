package com.switchpay.common;

import com.switchpay.payment.domain.InvalidTransitionException;
import com.switchpay.routing.NoAcquirerAvailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.Map;

/**
 * §17 — RFC 9457 {@code application/problem+json}. The {@code code} is the contract the test
 * suite asserts on; the {@code detail} message is not.
 *
 * The domain signals its failures with the error code as the exception *message*
 * ({@code throw new IllegalArgumentException("capture_exceeds_authorized")}), so this maps on
 * that string. It is a seam, not a design: the codes are duplicated between the thrower and this
 * table, and nothing fails the build if they drift apart. A typed exception per code would fix
 * it, and would be the right change the moment a third caller needs to branch on one.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private record Problem(HttpStatus status, String title) {}

    private static final Map<String, Problem> CODES = Map.ofEntries(
        Map.entry("capture_exceeds_authorized", new Problem(HttpStatus.UNPROCESSABLE_ENTITY, "Amount above remaining authorization")),
        Map.entry("refund_exceeds_captured", new Problem(HttpStatus.UNPROCESSABLE_ENTITY, "Amount above net captured")),
        Map.entry("void_after_capture", new Problem(HttpStatus.CONFLICT, "Cannot void a payment that has captures. Use a refund")),
        Map.entry("currency_mismatch", new Problem(HttpStatus.UNPROCESSABLE_ENTITY, "Operation currency does not match the payment")),
        Map.entry("pan_not_a_test_card", new Problem(HttpStatus.UNPROCESSABLE_ENTITY, "Only documented test BINs are accepted")),
        Map.entry("pan_invalid_luhn", new Problem(HttpStatus.UNPROCESSABLE_ENTITY, "Card number fails the Luhn check")),
        Map.entry("payment_authorization_unknown", new Problem(HttpStatus.CONFLICT, "Payment is in AUTH_UNKNOWN; wait for resolution")),
        Map.entry("payment_not_found", new Problem(HttpStatus.NOT_FOUND, "No such payment")),
        Map.entry("card_token_not_found", new Problem(HttpStatus.NOT_FOUND, "No such card token for this merchant")),
        Map.entry("merchant_id_required", new Problem(HttpStatus.BAD_REQUEST, "No authenticated merchant on this request")),
        Map.entry("threeds_signature_missing", new Problem(HttpStatus.UNAUTHORIZED, "Switch-Signature header is required")),
        Map.entry("threeds_signature_invalid", new Problem(HttpStatus.UNAUTHORIZED, "Switch-Signature does not match the body")),
        Map.entry("threeds_signature_expired", new Problem(HttpStatus.UNAUTHORIZED, "Switch-Signature is outside the 5-minute freshness window")),
        Map.entry("threeds_challenge_id_mismatch", new Problem(HttpStatus.BAD_REQUEST, "Signed challengeId does not match the path")),
        Map.entry("threeds_challenge_not_found", new Problem(HttpStatus.NOT_FOUND, "No such 3DS challenge")),
        Map.entry("threeds_challenge_already_resolved", new Problem(HttpStatus.CONFLICT, "This challenge already received an assertion")),
        Map.entry("threeds_challenge_expired", new Problem(HttpStatus.CONFLICT, "Challenge TTL passed before the assertion arrived"))
    );

    @ExceptionHandler(InvalidTransitionException.class)
    public ProblemDetail onInvalidTransition(InvalidTransitionException e) {
        return problem(e.getCode(), HttpStatus.CONFLICT,
                "Operation not permitted in current state", e.getMessage());
    }

    @ExceptionHandler(NoAcquirerAvailableException.class)
    public ProblemDetail onNoAcquirer(NoAcquirerAvailableException e) {
        return problem("no_acquirer_available", HttpStatus.SERVICE_UNAVAILABLE,
                "No eligible acquirer is currently reachable", e.getMessage());
    }

    @ExceptionHandler(CurrencyMismatchException.class)
    public ProblemDetail onCurrencyMismatch(CurrencyMismatchException e) {
        return problem("currency_mismatch", HttpStatus.UNPROCESSABLE_ENTITY,
                CODES.get("currency_mismatch").title(), e.getMessage());
    }

    /** A path variable that will not parse is the caller's mistake, not a server fault. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail onBadPathVariable(MethodArgumentTypeMismatchException e) {
        return problem("invalid_path_parameter", HttpStatus.BAD_REQUEST,
                "Path parameter is not well-formed", e.getName() + " could not be parsed");
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail onDomainRejection(RuntimeException e) {
        String code = e.getMessage();
        Problem known = code == null ? null : CODES.get(code);
        if (known == null) {
            // An unmapped message is a bug in this table or an genuinely unexpected failure.
            // Either way the caller gets no internals.
            logger.error("Unmapped domain exception", e);
            return problem("internal_error", HttpStatus.INTERNAL_SERVER_ERROR,
                    "Unexpected error", null);
        }
        return problem(code, known.status(), known.title(), null);
    }

    private ProblemDetail problem(String code, HttpStatus status, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setType(URI.create("https://switch.dev/errors/" + code));
        pd.setTitle(title);
        if (detail != null) {
            pd.setDetail(detail);
        }
        pd.setProperty("code", code);
        return pd;
    }
}
