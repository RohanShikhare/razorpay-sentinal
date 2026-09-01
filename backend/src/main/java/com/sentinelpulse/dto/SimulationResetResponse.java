package com.sentinelpulse.dto;

import java.time.Instant;

/**
 * Response body for POST /api/v1/simulations/reset.
 */
public record SimulationResetResponse(
        boolean reset,
        long restoredTransactions,
        Instant resetTimestamp,
        String message
) {
}
