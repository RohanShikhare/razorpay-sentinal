package com.sentinelpulse.dto;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Structured API response representing a generated recovery strategy proposal.
 * Includes authoritative financial metrics from Module 4 alongside bounded strategy estimates.
 */
public record RecoveryStrategyResponse(
        Long strategyId,
        Long incidentId,
        Long investigationId,
        Instant createdAt,
        StrategyStatus status,
        RecoveryStrategyType selectedStrategy,
        int rank,
        String rationale,
        double confidence,
        RiskLevel riskLevel,
        String affectedSegment,
        BigDecimal authoritativeRevenueAtRisk,
        BigDecimal authoritativeRecoverableAmount,
        double expectedRecoveryPercentage,
        BigDecimal estimatedRecoveryAmount,
        long estimatedAffectedTransactionCount,
        StoppingConditionsDto stoppingConditions,
        boolean requiresHumanApproval,
        List<AlternativeStrategyDto> alternatives,
        StrategySource source,
        String advisoryDisclaimer,
        String statusReason
) {
    public static final String DEFAULT_ADVISORY_DISCLAIMER =
            "Recovery strategy is advisory and has not been policy-approved or executed. Recovery execution is disabled until Policy Gate validation.";
}
