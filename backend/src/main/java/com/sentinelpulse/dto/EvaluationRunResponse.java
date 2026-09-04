package com.sentinelpulse.dto;

import com.sentinelpulse.dto.EvaluationMetricsDto.DetectionMetricsDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.DiagnosisMetricsDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.FinancialMetricsDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.PolicyMetricsEvaluationDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.RecoveryMetricsEvaluationDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.SafetyMetricsEvaluationDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.StrategyMetricsDto;

import java.time.Instant;
import java.util.List;

/**
 * Complete evaluation benchmark report and summary.
 */
public record EvaluationRunResponse(
        String runId,
        String evaluatorVersion,
        Instant startedAt,
        Instant completedAt,
        String status,
        long seed,
        int scenarioCount,
        int completedScenarios,
        int failedScenarios,
        int errorScenarios,
        double overallSentinelScore,
        FinancialMetricsDto financial,
        DetectionMetricsDto detection,
        DiagnosisMetricsDto diagnosis,
        StrategyMetricsDto strategy,
        PolicyMetricsEvaluationDto policy,
        RecoveryMetricsEvaluationDto recovery,
        SafetyMetricsEvaluationDto safety,
        List<IncidentEvaluationResultDto> incidents,
        String benchmarkDisclaimer
) {
}
