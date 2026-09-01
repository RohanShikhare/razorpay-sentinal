package com.sentinelpulse.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/v1/payments/generate.
 *
 * {@code seed} is optional - when provided, generation is reproducible;
 * when omitted, each call produces a fresh random dataset.
 */
public record PaymentGenerateRequest(

        @NotNull(message = "count is required")
        @Min(value = 1, message = "count must be at least 1")
        @Max(value = 10_000, message = "count must not exceed 10000")
        Integer count,

        Long seed
) {
}
