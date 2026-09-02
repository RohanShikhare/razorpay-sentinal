package com.sentinelpulse.service;

import com.sentinelpulse.dto.PaymentDto;
import com.sentinelpulse.dto.PaymentSummaryResponse;
import com.sentinelpulse.dto.PaymentSummaryResponse.CountBreakdown;
import com.sentinelpulse.dto.PaymentSummaryResponse.CountValueBreakdown;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PaymentSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-side operations on payments: filtered/paginated listing and the
 * aggregate summary. Kept separate from {@link PaymentGeneratorService} so
 * generation and querying can evolve independently.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * Finds payments matching any combination of the given filters. Any
     * argument may be null to skip that filter - this is also the entry
     * point later modules should use to fetch an arbitrary subset of
     * payments (by bank, method, status, error code, or time window).
     */
    public Page<PaymentDto> findPayments(PaymentStatus status, PaymentMethod paymentMethod, String bank,
                                          ErrorCode errorCode, Instant from, Instant to, Pageable pageable) {
        var spec = PaymentSpecifications.filter(status, paymentMethod, bank, errorCode, from, to);
        return paymentRepository.findAll(spec, pageable).map(PaymentDto::from);
    }

    public PaymentSummaryResponse getSummary() {
        long total = paymentRepository.count();

        Map<PaymentStatus, Long> statusCounts = new EnumMap<>(PaymentStatus.class);
        for (Object[] row : paymentRepository.countGroupedByStatus()) {
            statusCounts.put((PaymentStatus) row[0], (Long) row[1]);
        }

        Map<PaymentStatus, BigDecimal> statusValues = new EnumMap<>(PaymentStatus.class);
        for (Object[] row : paymentRepository.sumAmountGroupedByStatus()) {
            statusValues.put((PaymentStatus) row[0], (BigDecimal) row[1]);
        }

        long successful = statusCounts.getOrDefault(PaymentStatus.SUCCESS, 0L);
        long failed = statusCounts.getOrDefault(PaymentStatus.FAILED, 0L);
        long pending = statusCounts.getOrDefault(PaymentStatus.PENDING, 0L);

        BigDecimal totalValue = nullToZero(paymentRepository.sumAllAmount());
        BigDecimal successfulValue = nullToZero(statusValues.get(PaymentStatus.SUCCESS));
        BigDecimal failedValue = nullToZero(statusValues.get(PaymentStatus.FAILED));

        double successRate = rate(successful, total);
        double failureRate = rate(failed, total);

        Map<String, Long> failedCountByPaymentMethod = new HashMap<>();
        Map<String, BigDecimal> failedValueByPaymentMethod = new HashMap<>();
        for (Object[] row : paymentRepository.aggregateFailedByPaymentMethod()) {
            String key = ((PaymentMethod) row[0]).name();
            failedCountByPaymentMethod.put(key, (Long) row[1]);
            failedValueByPaymentMethod.put(key, nullToZero((BigDecimal) row[2]));
        }

        Map<String, Long> failedCountByBank = new HashMap<>();
        Map<String, BigDecimal> failedValueByBank = new HashMap<>();
        for (Object[] row : paymentRepository.aggregateFailedByBank()) {
            String key = (String) row[0];
            failedCountByBank.put(key, (Long) row[1]);
            failedValueByBank.put(key, nullToZero((BigDecimal) row[2]));
        }

        List<CountValueBreakdown> byPaymentMethod = new ArrayList<>();
        for (Object[] row : paymentRepository.aggregateByPaymentMethod()) {
            String key = ((PaymentMethod) row[0]).name();
            long count = (Long) row[1];
            long segmentFailed = failedCountByPaymentMethod.getOrDefault(key, 0L);
            BigDecimal segmentFailedValue = failedValueByPaymentMethod.getOrDefault(key, zero());
            byPaymentMethod.add(new CountValueBreakdown(
                    key, count, nullToZero((BigDecimal) row[2]), segmentFailed, segmentFailedValue, rate(segmentFailed, count)));
        }

        List<CountValueBreakdown> byBank = new ArrayList<>();
        for (Object[] row : paymentRepository.aggregateByBank()) {
            String key = (String) row[0];
            long count = (Long) row[1];
            long segmentFailed = failedCountByBank.getOrDefault(key, 0L);
            BigDecimal segmentFailedValue = failedValueByBank.getOrDefault(key, zero());
            byBank.add(new CountValueBreakdown(
                    key, count, nullToZero((BigDecimal) row[2]), segmentFailed, segmentFailedValue, rate(segmentFailed, count)));
        }

        List<CountBreakdown> byErrorCode = new ArrayList<>();
        for (Object[] row : paymentRepository.aggregateByErrorCodeExcludingNone()) {
            byErrorCode.add(new CountBreakdown(
                    ((ErrorCode) row[0]).name(), (Long) row[1], nullToZero((BigDecimal) row[2]), rate((Long) row[1], failed)));
        }

        return new PaymentSummaryResponse(
                total, successful, failed, pending,
                totalValue, successfulValue, failedValue,
                successRate, failureRate,
                byPaymentMethod, byBank, byErrorCode
        );
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static double rate(long part, long total) {
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
