package com.sentinelpulse.dto;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.StoppingReason;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Structured response containing the full results of a recovery execution simulation (Module 10).
 *
 * <p>Exposes the complete comparison:
 * <ul>
 *   <li>Authoritative Truth (Module 4)</li>
 *   <li>AI Proposed Estimate (Module 8)</li>
 *   <li>Policy Enforced Bounds (Module 9)</li>
 *   <li>Actual Simulated Recovery (Module 10)</li>
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
            "Simulation environment only. No real Razorpay APIs or live payment gateways were invoked. Recovery amounts are measured from synthetic payment replay simulation obeying Module 9 Policy Gate limits.";
}
