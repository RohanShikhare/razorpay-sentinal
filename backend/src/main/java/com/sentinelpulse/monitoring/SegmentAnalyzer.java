package com.sentinelpulse.monitoring;

import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.monitoring.PaymentStatistics.SegmentStats;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Compares baseline-period vs current-period statistics across the segment
 * dimensions the detection engine cares about: bank, payment method, their
 * combination, and merchant. This is what lets the engine notice "Bank X +
 * UPI" is degraded even when the system-wide failure rate barely moves.
 *
 * Pure and side-effect free - operates only on the payment lists it's given.
 */
public final class SegmentAnalyzer {

    private SegmentAnalyzer() {
    }

    public enum Dimension {
        BANK, PAYMENT_METHOD, BANK_PAYMENT_METHOD, MERCHANT
    }

    /**
     * One segment's baseline-vs-current comparison.
     */
    public record SegmentSignal(
            Dimension dimension,
            String label,
            String bank,
            PaymentMethod paymentMethod,
            String merchantId,
            SegmentStats baseline,
            SegmentStats current,
            double failureRateIncrease
    ) {
    }

    /**
     * Evaluates every segment across all four dimensions and returns those
     * with enough combined volume ({@code minSampleSize}) in both periods
     * to be statistically meaningful, sorted by failure-rate increase
     * (largest first).
     */
    public static List<SegmentSignal> evaluate(List<Payment> baseline, List<Payment> current, int minSampleSize) {
        List<SegmentSignal> signals = new ArrayList<>();

        signals.addAll(evaluateDimension(Dimension.BANK, baseline, current, Payment::getBank,
                bank -> bank, minSampleSize));
        signals.addAll(evaluateDimension(Dimension.PAYMENT_METHOD, baseline, current,
                p -> p.getPaymentMethod().name(), method -> method, minSampleSize));
        signals.addAll(evaluateDimension(Dimension.BANK_PAYMENT_METHOD, baseline, current,
                p -> p.getBank() + "|" + p.getPaymentMethod().name(),
                key -> key.replace("|", " + "), minSampleSize));
        signals.addAll(evaluateDimension(Dimension.MERCHANT, baseline, current, Payment::getMerchantId,
                merchant -> merchant, minSampleSize));

        signals.sort((a, b) -> Double.compare(b.failureRateIncrease(), a.failureRateIncrease()));
        return signals;
    }

    private static List<SegmentSignal> evaluateDimension(
            Dimension dimension, List<Payment> baseline, List<Payment> current,
            Function<Payment, String> keyFn, Function<String, String> labelFn, int minSampleSize) {

        Map<String, SegmentStats> baselineByKey = groupStats(baseline, keyFn);
        Map<String, SegmentStats> currentByKey = groupStats(current, keyFn);

        Set<String> keys = new HashSet<>(baselineByKey.keySet());
        keys.addAll(currentByKey.keySet());

        List<SegmentSignal> result = new ArrayList<>();
        for (String key : keys) {
            SegmentStats baselineStats = baselineByKey.getOrDefault(key, SegmentStats.EMPTY);
            SegmentStats currentStats = currentByKey.getOrDefault(key, SegmentStats.EMPTY);

            // Can't fairly judge a segment with no baseline history, or too
            // little combined volume to trust the percentage.
            if (baselineStats.total() == 0 || (baselineStats.total() + currentStats.total()) < minSampleSize) {
                continue;
            }

            double increase = currentStats.failureRate() - baselineStats.failureRate();
            result.add(toSignal(dimension, labelFn.apply(key), key, baselineStats, currentStats, increase));
        }
        return result;
    }

    private static SegmentSignal toSignal(Dimension dimension, String label, String key,
                                           SegmentStats baseline, SegmentStats current, double increase) {
        String bank = dimension == Dimension.BANK ? key
                : dimension == Dimension.BANK_PAYMENT_METHOD ? key.split("\\|")[0] : null;
        PaymentMethod method = dimension == Dimension.PAYMENT_METHOD ? PaymentMethod.valueOf(key)
                : dimension == Dimension.BANK_PAYMENT_METHOD ? PaymentMethod.valueOf(key.split("\\|")[1]) : null;
        String merchantId = dimension == Dimension.MERCHANT ? key : null;

        return new SegmentSignal(dimension, label, bank, method, merchantId, baseline, current, increase);
    }

    private static Map<String, SegmentStats> groupStats(List<Payment> payments, Function<Payment, String> keyFn) {
        return payments.stream().collect(Collectors.groupingBy(
                keyFn, Collectors.collectingAndThen(Collectors.toList(), PaymentStatistics::compute)));
    }
}
