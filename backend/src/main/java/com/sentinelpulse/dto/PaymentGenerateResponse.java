package com.sentinelpulse.dto;

import java.time.Instant;

/**
 * Response body for POST /api/v1/payments/generate.
 */
public record PaymentGenerateResponse(
        int requestedCount,
        int generatedCount,
        Instant windowStart,
        Instant windowEnd,
        Instant generatedAt
) {
}
