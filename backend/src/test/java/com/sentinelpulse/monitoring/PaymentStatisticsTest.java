package com.sentinelpulse.monitoring;

import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.monitoring.PaymentStatistics.SegmentStats;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentStatisticsTest {

    private static Payment payment(BigDecimal amount, PaymentStatus status) {
        return new Payment("merchant_001", "customer_00001", amount, "INR", Instant.now(),
                PaymentMethod.UPI, "Bank A", status, status == PaymentStatus.FAILED ? ErrorCode.BANK_TIMEOUT : ErrorCode.NONE);
    }

    @Test
    void emptyListProducesZeroedStats() {
        SegmentStats stats = PaymentStatistics.compute(List.of());
        assertEquals(0, stats.total());
        assertEquals(0.0, stats.successRate());
        assertEquals(0.0, stats.failureRate());
        assertEquals(0, BigDecimal.ZERO.compareTo(stats.totalValue()));
    }

    @Test
    void computesCountsAndRatesCorrectly() {
        List<Payment> payments = List.of(
                payment(BigDecimal.valueOf(100), PaymentStatus.SUCCESS),
                payment(BigDecimal.valueOf(100), PaymentStatus.SUCCESS),
                payment(BigDecimal.valueOf(100), PaymentStatus.SUCCESS),
                payment(BigDecimal.valueOf(100), PaymentStatus.FAILED),
                payment(BigDecimal.valueOf(100), PaymentStatus.PENDING)
        );

        SegmentStats stats = PaymentStatistics.compute(payments);

        assertEquals(5, stats.total());
        assertEquals(3, stats.successful());
        assertEquals(1, stats.failed());
        assertEquals(1, stats.pending());
        assertEquals(60.0, stats.successRate());
        assertEquals(20.0, stats.failureRate());
        assertEquals(0, BigDecimal.valueOf(500).setScale(2).compareTo(stats.totalValue()));
        assertEquals(0, BigDecimal.valueOf(100).setScale(2).compareTo(stats.failedValue()));
    }

    @Test
    void successAndFailedAndPendingCountsSumToTotal() {
        List<Payment> payments = List.of(
                payment(BigDecimal.TEN, PaymentStatus.SUCCESS),
                payment(BigDecimal.TEN, PaymentStatus.FAILED),
                payment(BigDecimal.TEN, PaymentStatus.FAILED),
                payment(BigDecimal.TEN, PaymentStatus.PENDING)
        );

        SegmentStats stats = PaymentStatistics.compute(payments);
        assertEquals(stats.total(), stats.successful() + stats.failed() + stats.pending());
    }

    @Test
    void rateHandlesZeroTotalWithoutDividingByZero() {
        assertEquals(0.0, PaymentStatistics.rate(0, 0));
    }

    @Test
    void rateMatchesPartOverTotalTimesOneHundred() {
        double rate = PaymentStatistics.rate(317, 1000);
        assertTrue(Math.abs(rate - 31.7) < 0.01);
    }
}
