package com.sentinelpulse.dto;

import com.sentinelpulse.ai.ControlledAction;
import com.sentinelpulse.ai.DiagnosisStatus;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.entity.IncidentType;

import java.time.Instant;
import java.util.List;

/**
 * Strictly structured response representing an AI incident diagnosis and advisory recommendation.
 */
public record AiDiagnosisResponse(
        Long diagnosisId,
        Long incidentId,
        Instant createdAt,
        String provider,
        String model,
        DiagnosisStatus status,
        IncidentType incidentType,
        String rootCause,
        Double confidence,
        String reasoning,
        List<String> evidence,
        String affectedSegment,
        ControlledAction recommendedAction,
        Double estimatedRecoveryPercentage,
        RiskLevel riskLevel,
        Boolean requiresHumanApproval,
        String errorMessage
) {
}
