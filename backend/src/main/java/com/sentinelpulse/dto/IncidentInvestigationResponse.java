package com.sentinelpulse.dto;

import com.sentinelpulse.ai.InvestigationStatus;
import com.sentinelpulse.entity.IncidentType;

import java.time.Instant;
import java.util.List;

/**
 * Structured response for a deep root-cause investigation.
 */
public record IncidentInvestigationResponse(
        Long investigationId,
        Long incidentId,
        Instant createdAt,
        Instant completedAt,
        InvestigationStatus status,
        String provider,
        String model,
        IncidentType incidentType,
        String summary,
        String strongestRootCause,
        double overallConfidence,
        String affectedSegment,
        List<RootCauseHypothesis> hypotheses,
        List<String> supportingEvidence,
        List<String> contradictingEvidence,
        String reasoning,
        String uncertainty,
        boolean requiresHumanReview,
        String statusReason
) {
}
