package com.sentinelpulse.dto;

import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.SimulationScenario;

import java.time.Instant;

/**
 * Response body for POST /api/v1/simulations/run.
 *
 * Reports only what was simulated - this never claims an incident was
 * "detected"; that judgment belongs to the future Monitoring & Incident
 * Detection Engine.
 */
public record SimulationRunResponse(
        SimulationScenario scenario,
        long affectedTransactions,
        long modifiedTransactions,
        String targetBank,
        PaymentMethod targetPaymentMethod,
        String targetMerchantId,
        double failureRateBefore,
        double failureRateAfter,
        Instant simulationTimestamp,
        boolean previousSimulationReset,
        String message
) {
}
