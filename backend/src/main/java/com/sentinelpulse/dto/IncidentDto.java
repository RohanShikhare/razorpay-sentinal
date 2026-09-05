package com.sentinelpulse.dto;

import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * API representation of an {@link Incident}. {@code rootCause} is null
 * until the AI investigation engine assigns one.
 */
public record IncidentDto(
        Long id,
        IncidentType type,
        IncidentSeverity severity,
        IncidentStatus status,
        Instant detectedAt,
        Instant startTime,
        Instant endTime,
        long affectedTransactions,
        BigDecimal affectedTransactionValue,
        BigDecimal failedTransactionValue,
        BigDecimal revenueAtRisk,
        double confidence,
        String rootCause,
        double baselineFailureRate,
        double currentFailureRate,
        double failureRateIncrease,
        String targetBank,
        PaymentMethod targetPaymentMethod,
        String targetMerchantId,
        int monitoringWindowMinutes
) {
    public static IncidentDto from(Incident incident) {
        return new IncidentDto(
                incident.getId(), incident.getType(), incident.getSeverity(), incident.getStatus(),
                incident.getDetectedAt(), incident.getStartTime(), incident.getEndTime(),
                incident.getAffectedTransactions(), incident.getAffectedTransactionValue(),
                incident.getFailedTransactionValue(), incident.getRevenueAtRisk(), incident.getConfidence(),
                incident.getRootCause(), incident.getBaselineFailureRate(), incident.getCurrentFailureRate(),
                incident.getFailureRateIncrease(), incident.getTargetBank(), incident.getTargetPaymentMethod(),
                incident.getTargetMerchantId(), incident.getMonitoringWindowMinutes()
        );
    }
}
