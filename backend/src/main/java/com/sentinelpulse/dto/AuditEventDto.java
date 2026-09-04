package com.sentinelpulse.dto;

import com.sentinelpulse.audit.AuditActorType;
import com.sentinelpulse.audit.AuditEventCategory;
import com.sentinelpulse.audit.AuditEventType;
import com.sentinelpulse.audit.AuditSeverity;
import com.sentinelpulse.audit.AuditStatus;
import com.sentinelpulse.entity.AuditEvent;

import java.time.Instant;

/**
 * DTO representing an individual audit trail event.
 */
public record AuditEventDto(
        Long id,
        String eventId,
        Long incidentId,
        Long executionId,
        Long strategyId,
        Long policyEvaluationId,
        AuditEventType eventType,
        AuditEventCategory eventCategory,
        AuditActorType actorType,
        String actorId,
        AuditSeverity severity,
        AuditStatus status,
        String summary,
        String reason,
        String evidenceJson,
        String metadataJson,
        Instant timestamp,
        String previousEventHash,
        String eventHash
) {
    public static AuditEventDto fromEntity(AuditEvent event) {
        return new AuditEventDto(
                event.getId(),
                event.getEventId(),
                event.getIncidentId(),
                event.getExecutionId(),
                event.getStrategyId(),
                event.getPolicyEvaluationId(),
                event.getEventType(),
                event.getEventCategory(),
                event.getActorType(),
                event.getActorId(),
                event.getSeverity(),
                event.getStatus(),
                event.getSummary(),
                event.getReason(),
                event.getEvidenceJson(),
                event.getMetadataJson(),
                event.getTimestamp(),
                event.getPreviousEventHash(),
                event.getEventHash()
        );
    }
}
