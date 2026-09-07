package com.switchpay.idempotency;

import com.switchpay.merchant.MerchantAuthFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * §7 — claims the {@code Idempotency-Key} before the handler runs and replays the stored
 * response on a duplicate.
 *
 * Runs after {@link MerchantAuthFilter} ({@code @Order(1)}): idempotency keys are scoped per
 * merchant, and that merchant identity now comes from a verified API key, not a header the
 * caller could set to anything (NR-4).
 */
@Component
@Order(2)
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyFilter.class);

    /** §7: "required on every mutating endpoint". A GET has nothing to make idempotent. */
    private static final Set<String> MUTATING = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String idempotencyKey = request.getHeader("Idempotency-Key");
        UUID merchantId = (UUID) request.getAttribute(MerchantAuthFilter.MERCHANT_ID_ATTRIBUTE);

        if (idempotencyKey == null || merchantId == null || !MUTATING.contains(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        // The body must be read here, before the handler, and still be readable by the handler
        // afterwards — that is the whole reason CachedBodyRequest exists. Fingerprinting an
        // unread body silently makes every request look identical, which turns
        // idempotency_key_reuse into a replay of the wrong response.
        CachedBodyRequest cachedRequest = new CachedBodyRequest(request);
        byte[] fingerprint = RequestFingerprint.compute(
                request.getMethod(), request.getRequestURI(), cachedRequest.bodyAsString());

        IdempotencyService.ClaimResult result = idempotencyService.claimKey(merchantId, idempotencyKey, fingerprint);

        if (result instanceof IdempotencyService.ClaimResult.AlreadyCompleted completed) {
            response.setStatus(completed.responseStatus());
            response.setContentType("application/json");
            response.getWriter().write(completed.responseBody());
            return;
        }
        if (result instanceof IdempotencyService.ClaimResult.InProgress) {
            writeProblem(response, 409, "request_in_progress",
                    "A request with this Idempotency-Key is currently in flight");
            return;
        }
        if (result instanceof IdempotencyService.ClaimResult.KeyReuse) {
            writeProblem(response, 422, "idempotency_key_reuse",
                    "This Idempotency-Key was already used with a different request body");
            return;
        }

        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);
        try {
            filterChain.doFilter(cachedRequest, responseWrapper);

            String responseBody = new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
            int status = responseWrapper.getStatus();

            if (status >= 500) {
                // A 5xx is not an answer, it is an absence of one. Storing it would replay the
                // failure for the whole 24h TTL. This branch matters more than it looks: the §17
                // exception handler turns thrown failures into ordinary responses, so the catch
                // below never sees them.
                idempotencyService.releaseKey(merchantId, idempotencyKey);
            } else {
                idempotencyService.completeKey(merchantId, idempotencyKey, status, responseBody, null);
            }
            responseWrapper.copyBodyToResponse();
        } catch (Exception e) {
            logger.warn("Releasing idempotency key after handler failure", e);
            idempotencyService.releaseKey(merchantId, idempotencyKey);
            responseWrapper.copyBodyToResponse();
            throw e;
        }
    }

    /** RFC 9457, matching {@code common/ApiExceptionHandler} — a filter cannot use @ControllerAdvice. */
    private void writeProblem(HttpServletResponse response, int status, String code, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"type":"https://switch.dev/errors/%s","title":"%s","status":%d,"code":"%s","detail":"%s"}"""
                .formatted(code, code, status, code, detail));
    }
}
