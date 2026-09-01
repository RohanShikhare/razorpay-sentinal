package com.sentinelpulse.monitoring;

import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Computes deterministic statistics over a list of payments. Pure and
 * side-effect free - it never touches the database itself, so it's easy to
 * unit test and easy to reuse for baseline, current-window, and per-segment
 * calculations alike.
 */
public final class PaymentStatistics {

    private PaymentStatistics() {
    }

    /**
     * Aggregate statistics for one population of payments (the whole
     * dataset, a time window, or a segment within one).
     */
    public record SegmentStats(
            long total,
            long successful,
            long failed,
            long pending,
            double successRate,
            double failureRate,
            BigDecimal totalValue,
            BigDecimal failedValue
    ) {
        public static final SegmentStats EMPTY = new SegmentStats(
                0, 0, 0, 0, 0.0, 0.0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    public static SegmentStats compute(List<Payment> payments) {
        if (payments.isEmpty()) {
            return SegmentStats.EMPTY;
        }

        long total = payments.size();
        long successful = 0;
        long failed = 0;
        long pending = 0;
        BigDecimal totalValue = BigDecimal.ZERO;
        BigDecimal failedValue = BigDecimal.ZERO;

        for (Payment payment : payments) {
            totalValue = totalValue.add(payment.getAmount());
            switch (payment.getStatus()) {
                case SUCCESS -> successful++;
                case FAILED -> {
                    failed++;
                    failedValue = failedValue.add(payment.getAmount());
                }
                case PENDING -> pending++;
                default -> {
                }
            }
        }

        return new SegmentStats(
                total, successful, failed, pending,
                rate(successful, total), rate(failed, total),
                totalValue.setScale(2, RoundingMode.HALF_UP),
                failedValue.setScale(2, RoundingMode.HALF_UP)
        );
    }

    public static long failedCount(List<Payment> payments) {
        return payments.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
    }

    /**
     * Percentage (0-100), rounded to 2 decimal places, of {@code part} out
     * of {@code total}. Returns 0 when total is 0 rather than dividing by zero.
     */
    public static double rate(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
