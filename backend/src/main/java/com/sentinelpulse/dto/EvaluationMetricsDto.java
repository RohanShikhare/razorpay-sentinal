package com.sentinelpulse.dto;

import java.math.BigDecimal;

/**
 * Encapsulates the metric models across all 7 evaluation dimensions.
 */
public final class EvaluationMetricsDto {

    private EvaluationMetricsDto() {}

    public record FinancialMetricsDto(
            BigDecimal totalRevenueAtRisk,
            BigDecimal totalRecoverableRevenue,
            BigDecimal totalRecoveredRevenue,
            BigDecimal totalUnrecoveredRevenue,
            double financialRecoveryRate,
            BigDecimal baselineNoRecoveryAmount,
            BigDecimal baselineNaiveRetryAmount,
            BigDecimal recoveryUpliftVsNoRecovery,
            BigDecimal recoveryUpliftVsNaiveRetry,
            BigDecimal recoveryEfficiency
    ) {}

    public record DetectionMetricsDto(
            int totalScenariosEvaluated,
            int truePositives,
            int falsePositives,
            int falseNegatives,
            int trueNegatives,
            double precision,
            double recall,
            double f1Score,
            long averageDetectionLatencyMs
    ) {}

    public record DiagnosisMetricsDto(
            int totalDiagnosesEvaluated,
            int correctDiagnoses,
            int incorrectDiagnoses,
            int inconclusiveDiagnoses,
            double diagnosisAccuracy,
            double averageConfidence
    ) {}

    public record StrategyMetricsDto(
            int totalStrategiesEvaluated,
            int safeStrategiesProposed,
            int unsafeStrategiesProposed,
            double strategyQualityScore
    ) {}

    public record PolicyMetricsEvaluationDto(
            int totalPolicyEvaluations,
            int approvedCount,
            int approvedWithLimitsCount,
            int humanReviewCount,
            int rejectedCount,
            int executionAllowedCount,
            int executionBlockedCount,
            double policyComplianceRate
    ) {}

    public record RecoveryMetricsEvaluationDto(
            int eligibleTransactions,
            int attemptedTransactions,
            int successfulRecoveries,
            int failedRecoveries,
            int skippedTransactions,
            double transactionRecoveryRate,
            double averageAttemptsPerRecovery,
            int circuitBreakerTrips
    ) {}

    public record SafetyMetricsEvaluationDto(
            int safetyScore,
            int policyBypasses,
            int unauthorizedExecutions,
            int financialBoundViolations,
            int retryLimitViolations,
            int resolvedIncidentRetries,
            int duplicateRecoveries,
            int auditIntegrityFailures,
            int safetyScenariosPassed,
            int safetyScenariosFailed
    ) {}
}
