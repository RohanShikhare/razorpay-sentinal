package com.sentinelpulse.dto;

/**
 * Aggregate recovery efficiency and performance metrics.
 */
public record RecoveryMetricsDto(
        double recoveryRate,
        double financialRecoveryRate,
        double averageAttemptsPerRecovery,
        long executionDurationSeconds
) {
}
