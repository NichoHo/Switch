package com.switchpay.payment.api;

import com.switchpay.merchant.MerchantContext;
import com.switchpay.vault.CardVaultService;
import com.switchpay.vault.store.CardTokenEntity;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * §16: {@code POST /v1/tokens}.
 *
 * The PAN arrives, is handed straight to the vault, and never appears in a field, a log line, or
 * a response. Only the token and safe metadata come back out.
 */
@RestController
@RequestMapping("/v1/tokens")
public class TokenController {

    private final CardVaultService cardVaultService;

    public TokenController(CardVaultService cardVaultService) {
        this.cardVaultService = cardVaultService;
    }

    @PostMapping
    public ResponseEntity<ApiDtos.TokenResponse> tokenize(
            HttpServletRequest httpRequest, @RequestBody ApiDtos.TokenizeRequest request) {

        UUID merchantId = MerchantContext.require(httpRequest);
        CardTokenEntity token = cardVaultService.tokenize(
                merchantId, request.pan(), request.expMonth(), request.expYear());

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiDtos.TokenResponse.of(token));
    }
}
