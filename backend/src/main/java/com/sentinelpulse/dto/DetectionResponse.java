package com.sentinelpulse.dto;

import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response body for POST /api/v1/monitoring/detect.
 *
 * Reports what the deterministic engine observed and decided - never a root
 * cause. When {@code incidentDetected} is false, every other field still
 * reflects the real measurement; {@code message} explains why it didn't
 * cross the detection threshold.
 */
public record DetectionResponse(
        boolean incidentDetected,
        Long incidentId,
        IncidentType incidentType,
        IncidentSeverity severity,
        double baselineFailureRate,
        double currentFailureRate,
        double failureRateIncrease,
        Double relativeIncreasePercent,
        long affectedTransactions,
        BigDecimal affectedTransactionValue,
        BigDecimal failedTransactionValue,
        BigDecimal revenueAtRisk,
        Double confidence,
        int monitoringWindowMinutes,
        String targetBank,
        PaymentMethod targetPaymentMethod,
        String targetMerchantId,
        List<SegmentBreakdownDto> topAffectedSegments,
        Instant timestamp,
        String message
) {
}
