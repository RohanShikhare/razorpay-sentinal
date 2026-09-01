package com.sentinelpulse.dto;

import java.time.Instant;

/**
 * Response body for GET /api/v1/health.
 */
public record HealthResponse(
        String status,
        String service,
        String module,
        Instant timestamp
) {
}
