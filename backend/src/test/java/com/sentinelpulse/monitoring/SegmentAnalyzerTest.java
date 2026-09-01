package com.sentinelpulse.monitoring;

import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.monitoring.SegmentAnalyzer.SegmentSignal;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentAnalyzerTest {

    private static Payment payment(String bank, PaymentMethod method, String merchant, PaymentStatus status) {
        return new Payment(merchant, "customer_00001", BigDecimal.valueOf(500), "INR", Instant.now(),
                method, bank, status, status == PaymentStatus.FAILED ? ErrorCode.BANK_TIMEOUT : ErrorCode.NONE);
    }

    private static List<Payment> segment(String bank, PaymentMethod method, String merchant,
                                          int total, double failureRate) {
        List<Payment> payments = new ArrayList<>();
        int failed = (int) Math.round(total * failureRate);
        for (int i = 0; i < total; i++) {
            payments.add(payment(bank, method, merchant, i < failed ? PaymentStatus.FAILED : PaymentStatus.SUCCESS));
        }
        return payments;
    }

    @Test
    void identifiesConcentratedSegmentDegradation() {
        List<Payment> baseline = new ArrayList<>();
        baseline.addAll(segment("Bank X", PaymentMethod.UPI, "merchant_001", 100, 0.05));
        baseline.addAll(segment("Bank A", PaymentMethod.UPI, "merchant_002", 100, 0.05));

        List<Payment> current = new ArrayList<>();
        current.addAll(segment("Bank X", PaymentMethod.UPI, "merchant_001", 50, 0.60));
        current.addAll(segment("Bank A", PaymentMethod.UPI, "merchant_002", 50, 0.05));

        List<SegmentSignal> signals = SegmentAnalyzer.evaluate(baseline, current, 15);

        assertFalse(signals.isEmpty());
        SegmentSignal top = signals.get(0);
        assertTrue(top.label().contains("Bank X"), "top signal should be Bank X, got " + top.label());
        assertTrue(top.failureRateIncrease() > 40.0, "expected a large increase, got " + top.failureRateIncrease());

        // Bank A's segment should show close to zero increase.
        boolean bankAHasSmallIncrease = signals.stream()
                .filter(s -> s.label().contains("Bank A"))
                .allMatch(s -> s.failureRateIncrease() < 5.0);
        assertTrue(bankAHasSmallIncrease, "Bank A segments should stay close to baseline");
    }

    @Test
    void excludesSegmentsBelowMinimumSampleSize() {
        List<Payment> baseline = segment("Bank Y", PaymentMethod.WALLET, "merchant_003", 3, 0.0);
        List<Payment> current = segment("Bank Y", PaymentMethod.WALLET, "merchant_003", 3, 1.0);

        List<SegmentSignal> signals = SegmentAnalyzer.evaluate(baseline, current, 15);

        assertTrue(signals.isEmpty(), "tiny segments below the minimum sample size should be excluded");
    }

    @Test
    void skipsSegmentsWithNoBaselineHistory() {
        List<Payment> baseline = List.of();
        List<Payment> current = segment("Bank Z", PaymentMethod.CARD, "merchant_004", 50, 1.0);

        List<SegmentSignal> signals = SegmentAnalyzer.evaluate(baseline, current, 15);

        assertTrue(signals.isEmpty(), "a segment with no baseline history can't be fairly judged");
    }

    @Test
    void merchantDimensionIdentifiesTheAffectedMerchant() {
        List<Payment> baseline = new ArrayList<>();
        baseline.addAll(segment("Bank A", PaymentMethod.CARD, "merchant_hot", 100, 0.05));
        baseline.addAll(segment("Bank B", PaymentMethod.UPI, "merchant_cold", 100, 0.05));

        List<Payment> current = new ArrayList<>();
        current.addAll(segment("Bank A", PaymentMethod.CARD, "merchant_hot", 50, 0.55));
        current.addAll(segment("Bank B", PaymentMethod.UPI, "merchant_cold", 50, 0.05));

        List<SegmentSignal> signals = SegmentAnalyzer.evaluate(baseline, current, 15);
        SegmentSignal topMerchantSignal = signals.stream()
                .filter(s -> s.dimension() == SegmentAnalyzer.Dimension.MERCHANT)
                .findFirst()
                .orElseThrow();

        assertEquals("merchant_hot", topMerchantSignal.merchantId());
        assertTrue(topMerchantSignal.failureRateIncrease() > 30.0);
    }
}
