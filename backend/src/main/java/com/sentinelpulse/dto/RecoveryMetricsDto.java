package com.sentinelpulse.dto;

/**
 * Aggregate recovery efficiency and performance metrics (Module 10).
 */
public record RecoveryMetricsDto(
        double recoveryRate,
        double financialRecoveryRate,
        double averageAttemptsPerRecovery,
        long executionDurationSeconds
) {
}
