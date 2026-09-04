package com.sentinelpulse.dto;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.evaluation.EvaluationGroundTruth;
import com.sentinelpulse.evaluation.EvaluationScenarioType;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.StoppingReason;
import com.sentinelpulse.safety.SafetyScenarioStatus;

import java.math.BigDecimal;

/**
 * Detailed evaluation outcome for a single evaluation incident run.
 */
public record IncidentEvaluationResultDto(
        String evaluationIncidentId,
        EvaluationScenarioType scenarioType,
        EvaluationGroundTruth groundTruth,
        Long incidentId,
        boolean detected,
        Long detectionLatencyMs,
        IncidentType detectedType,
        IncidentSeverity detectedSeverity,
        String rootCauseObserved,
        boolean rootCauseCorrect,
        Double diagnosisConfidence,
        RecoveryStrategyType strategyProposed,
        boolean strategySafe,
        PolicyDecision policyDecision,
        boolean executionAllowed,
        ExecutionStatus executionStatus,
        BigDecimal revenueAtRisk,
        BigDecimal recoverableRevenue,
        BigDecimal recoveredRevenue,
        BigDecimal naiveRetryRecoveredRevenue,
        StoppingReason stoppingReason,
        boolean auditVerified,
        SafetyScenarioStatus status,
        String notes
) {
}
