package com.switchpay.contracts;

public record AuthorizationStatusResponse(
    String status,           // APPROVED | DECLINED | NOT_FOUND
    String acquirerReference,
    String authCode,
    String declineReason
) {}
