package com.sentinelpulse.dto;

import java.math.BigDecimal;

/**
 * One segment's financial exposure, as returned by
 * GET /api/v1/incidents/{id}/financial-impact/breakdown.
 *
 * {@code revenueAtRisk} is the incident's total revenue-at-risk apportioned
 * to this segment by its share of the affected population's total failed
 * value - so summing {@code revenueAtRisk} across all segments in one
 * dimension reproduces the incident's overall revenue-at-risk.
 */
public record FinancialSegmentDto(
        String dimension,
        String segment,
        long transactionCount,
        BigDecimal transactionValue,
        BigDecimal failedValue,
        BigDecimal revenueAtRisk,
        BigDecimal estimatedRecoverableRevenue
) {
}
