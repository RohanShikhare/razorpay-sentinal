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
    /**
     * A segment's totals plus its own failure count/value/rate so the
     * dashboard's bank and payment-method breakdown tables
     * can show a failure rate per segment without extra round trips.
     */
    public record CountValueBreakdown(
            String key, long count, BigDecimal value,
            long failedCount, BigDecimal failedValue, double failureRate
    ) {
    }

    /**
     * An error code's count, total value, and share of all failures across
     * the dataset (not the same as {@code failureRate} above - every row
     * here is already a failure, so this is "what fraction of failures are
     * this error code", useful for the dashboard's error-code breakdown).
     */
    public record CountBreakdown(String key, long count, BigDecimal value, double percentageOfFailures) {
    }
}
