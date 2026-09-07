package com.switchpay.contracts;

public record AuthorizationResponse(
    String status,           // APPROVED | DECLINED
    String acquirerReference,
    String authCode,
    String declineReason     // null if approved
) {}
