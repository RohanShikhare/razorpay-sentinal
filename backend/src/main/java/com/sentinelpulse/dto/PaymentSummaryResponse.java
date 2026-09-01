package com.sentinelpulse.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Response body for GET /api/v1/payments/summary.
 *
 * Rates are expressed as percentages (0-100), rounded to two decimal
 * places. Breakdown value totals are computed by database aggregation,
 * not by loading every row into memory.
 */
public record PaymentSummaryResponse(
        long totalTransactions,
        long successfulTransactions,
        long failedTransactions,
        long pendingTransactions,
        BigDecimal totalTransactionValue,
        BigDecimal successfulTransactionValue,
        BigDecimal failedTransactionValue,
        double successRate,
        double failureRate,
        List<CountValueBreakdown> byPaymentMethod,
        List<CountValueBreakdown> byBank,
        List<CountBreakdown> byErrorCode
) {
    public record CountValueBreakdown(String key, long count, BigDecimal value) {
    }

    public record CountBreakdown(String key, long count) {
    }
}
