package com.sentinelpulse.dto;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.StoppingReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Structured response containing the full results of a recovery execution simulation.
 *
 * <p>Exposes the complete comparison:
 * <ul>
 *   <li>Authoritative Truth</li>
 *   <li>AI Proposed Estimate</li>
 *   <li>Policy Enforced Bounds</li>
 *   <li>Actual Simulated Recovery</li>
 * </ul>
 */
public record RecoveryExecutionResponse(
        Long executionId,
        Long incidentId,
        Long strategyId,
        Long policyEvaluationId,
        Instant startedAt,
        Instant completedAt,
        ExecutionStatus status,
        RecoveryStrategyType strategyType,
        long eligibleTransactions,
        long attemptedTransactions,
        long successfulRecoveries,
        long failedRecoveries,
        long skippedTransactions,
        BigDecimal recoveredAmount,
        BigDecimal attemptedAmount,
        BigDecimal remainingRecoverableAmount,
        BigDecimal authoritativeRevenueAtRisk,
        BigDecimal authoritativeRecoverableAmount,
        Double expectedRecoveryPercentage,
        RecoveryMetricsDto metrics,
        StoppingReason stoppingReason,
        String stoppingDetails,
        boolean executionAllowed,
        Integer appliedRetryLimit,
        Integer appliedRetryWindow,
        Integer appliedBackoff,
        String simulationVersion,
        List<RecoveryExecutionAttemptDto> attempts,
        String simulationDisclaimer
) {
    public static final String DEFAULT_SIMULATION_DISCLAIMER =
            "Simulation environment only. No real Razorpay APIs or live payment gateways were invoked. Recovery amounts are measured from synthetic payment replay simulation obeying Policy Gate limits.";
}
