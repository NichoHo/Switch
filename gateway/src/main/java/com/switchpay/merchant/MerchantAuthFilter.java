package com.switchpay.merchant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * §3 step 1 / §16 — {@code Authorization: Bearer sk_test_…}, looked up by hash.
 *
 * Runs before {@code IdempotencyFilter} ({@code @Order(2)}) so idempotency-key scoping is keyed
 * on a merchant identity that has actually been verified, not one a caller merely asserted. This
 * is the fix for NR-4: the request used to carry its own identity in an {@code X-Merchant-Id}
 * header that nothing checked.
 */
@Component
@Order(1)
public class MerchantAuthFilter extends OncePerRequestFilter {

    public static final String MERCHANT_ID_ATTRIBUTE = "switch.merchantId";

    private final MerchantRepository merchantRepository;

    public MerchantAuthFilter(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    /** Only the merchant-facing API needs a merchant. Admin, 3DS, and ops endpoints don't. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String rawKey = bearerToken(request.getHeader("Authorization"));
        if (rawKey == null) {
            writeUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }

        Optional<MerchantEntity> merchant = merchantRepository.findByApiKeyHash(ApiKeyHasher.hash(rawKey));
        if (merchant.isEmpty()) {
            writeUnauthorized(response, "No merchant matches this API key");
            return;
        }

        request.setAttribute(MERCHANT_ID_ATTRIBUTE, merchant.get().getId());
        filterChain.doFilter(request, response);
    }

    private String bearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        String key = authorizationHeader.substring("Bearer ".length()).trim();
        return key.isEmpty() ? null : key;
    }

    /** RFC 9457, matching {@code common/ApiExceptionHandler} — a filter cannot use @ControllerAdvice. */
    private void writeUnauthorized(HttpServletResponse response, String detail) throws IOException {
        response.setStatus(401);
        response.setContentType("application/problem+json");
        response.getWriter().write("""
                {"type":"https://switch.dev/errors/unauthorized","title":"Unauthorized",\
                "status":401,"code":"unauthorized","detail":"%s"}"""
                .formatted(detail));
    }
}
