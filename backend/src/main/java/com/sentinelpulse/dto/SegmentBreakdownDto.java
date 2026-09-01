package com.sentinelpulse.dto;

/**
 * One segment's baseline-vs-current comparison, as surfaced to API callers.
 * {@code dimension} is one of BANK, PAYMENT_METHOD, BANK_PAYMENT_METHOD, MERCHANT.
 */
public record SegmentBreakdownDto(
        String dimension,
        String label,
        long baselineTransactions,
        long currentTransactions,
        double baselineFailureRate,
        double currentFailureRate,
        double failureRateIncrease
) {
}
