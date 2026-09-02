package com.sentinelpulse.dto;

import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentType;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Structured evidence for a detected incident, shaped for a future AI
 * root-cause investigation module to consume directly - no LLM call happens
 * here, this is just packaging the same deterministic numbers the other
 * financial-impact endpoints expose into map-keyed breakdowns instead of
 * lists, which tends to be a more convenient shape for prompt construction.
 *
 * {@code timeSeries} is reserved for a future time-bucketed view and is
 * currently always empty - no time-series computation exists yet.
 */
public record IncidentEvidenceDto(
        Long incidentId,
        IncidentType incidentType,
        IncidentSeverity severity,
        double failureRate,
        double baselineFailureRate,
        double failureRateIncrease,
        long affectedTransactions,
        BigDecimal revenueAtRisk,
        BigDecimal estimatedRecoverableRevenue,
        BigDecimal nonRecoverableRevenue,
        Map<String, SegmentFinancialsDto> bankBreakdown,
        Map<String, SegmentFinancialsDto> paymentMethodBreakdown,
        Map<String, SegmentFinancialsDto> errorCodeBreakdown,
        Map<String, SegmentFinancialsDto> merchantBreakdown,
        Map<String, Object> timeSeries
) {
    public record SegmentFinancialsDto(
            long transactionCount,
            BigDecimal transactionValue,
            BigDecimal failedValue,
            BigDecimal revenueAtRisk,
            BigDecimal estimatedRecoverableRevenue
    ) {
    }
}
