package com.sentinelpulse.dto;

import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Deterministic structured evidence object constructed for AI consumption.
 * Encapsulates observed facts from Module 3 (Detection) and Module 4 (Financial Impact).
 */
public record AiEvidenceDto(
        // Incident Metadata & Core Metrics
        Long incidentId,
        IncidentType incidentType,
        IncidentSeverity severity,
        Instant detectedTime,
        long affectedTransactionCount,
        double currentFailureRate,
        double baselineFailureRate,
        double failureRateIncrease,
        Double relativeIncreasePercent,
        IncidentStatus incidentStatus,
        String targetBank,
        PaymentMethod targetPaymentMethod,
        String targetMerchantId,

        // Authoritative Financial Impact (Module 4)
        BigDecimal affectedTransactionValue,
        BigDecimal failedTransactionValue,
        BigDecimal revenueAtRisk,
        BigDecimal estimatedRecoverableRevenue,
        BigDecimal nonRecoverableRevenue,
        BigDecimal averageTransactionValue,

        // Segment & Comparative Evidence
        Map<String, SegmentDetail> bankBreakdown,
        Map<String, SegmentDetail> paymentMethodBreakdown,
        Map<String, ErrorCodeDetail> errorCodeBreakdown,
        Map<String, SegmentDetail> merchantBreakdown,
        ConcentrationSummary concentrationSummary,
        List<SegmentBreakdownDto> topAffectedSegments
) {
    public record SegmentDetail(
            long transactionCount,
            BigDecimal transactionValue,
            long failedCount,
            BigDecimal failedValue,
            double failureRate,
            double failurePercentageShare
    ) {
    }

    public record ErrorCodeDetail(
            String errorCode,
            long count,
            BigDecimal failedValue,
            String recoverabilityLevel,
            double recoverabilityRate,
            double shareOfFailures
    ) {
    }

    public record ConcentrationSummary(
            String dominantBank,
            Double dominantBankFailureShare,
            String dominantPaymentMethod,
            Double dominantPaymentMethodFailureShare,
            String dominantErrorCode,
            Double dominantErrorCodeFailureShare,
            String dominantMerchantId,
            Double dominantMerchantFailureShare
    ) {
    }
}
