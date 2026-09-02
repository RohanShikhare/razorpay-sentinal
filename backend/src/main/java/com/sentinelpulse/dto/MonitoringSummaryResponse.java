package com.sentinelpulse.dto;

import java.math.BigDecimal;

/**
 * Response body for GET /api/v1/monitoring/summary.
 *
 * {@code paymentHealth} is one of NORMAL, LOW, MEDIUM, HIGH, CRITICAL -
 * NORMAL when there are no active incidents, otherwise the highest severity
 * among currently-active incidents.
 */
public record MonitoringSummaryResponse(
        String paymentHealth,
        double successRate,
        double failureRate,
        double baselineFailureRate,
        double failureRateChange,
        BigDecimal revenueAtRisk,
        BigDecimal estimatedRecoverableRevenue,
        BigDecimal affectedTransactionValue,
        long activeIncidents,
        long affectedTransactions
) {
}
