package com.sentinelpulse.dto;

import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.SimulationScenario;

import java.time.Instant;

/**
 * Response body for GET /api/v1/simulations/status.
 */
public record SimulationStatusResponse(
        boolean active,
        SimulationScenario scenario,
        Instant startedAt,
        long affectedTransactions,
        long modifiedTransactions,
        String targetBank,
        PaymentMethod targetPaymentMethod,
        String targetMerchantId,
        double failureRateBefore,
        double failureRateAfter
) {
}
