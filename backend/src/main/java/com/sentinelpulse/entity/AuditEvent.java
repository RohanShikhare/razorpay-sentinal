package com.sentinelpulse.entity;

import com.sentinelpulse.audit.AuditActorType;
import com.sentinelpulse.audit.AuditEventCategory;
import com.sentinelpulse.audit.AuditEventType;
import com.sentinelpulse.audit.AuditSeverity;
import com.sentinelpulse.audit.AuditStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Persisted Audit Event (Module 11: Audit Trail & Decision History).
 *
 * <p>Captures an immutable, tamper-evident record of each incident response lifecycle step,
 * recording what occurred, which actor initiated it, what evidence was evaluated, and
 * what outcome was achieved.
 */
@Entity
@Table(
        name = "audit_events",
        indexes = {
                @Index(name = "idx_audit_events_incident_id", columnList = "incident_id"),
                @Index(name = "idx_audit_events_execution_id", columnList = "execution_id"),
                @Index(name = "idx_audit_events_event_type", columnList = "event_type"),
                @Index(name = "idx_audit_events_timestamp", columnList = "timestamp")
        }
)
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "incident_id")
    private Long incidentId;

    @Column(name = "execution_id")
    private Long executionId;

    @Column(name = "strategy_id")
    private Long strategyId;

    @Column(name = "policy_evaluation_id")
    private Long policyEvaluationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private AuditEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_category", nullable = false, length = 32)
    private AuditEventCategory eventCategory;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 32)
    private AuditActorType actorType;

    @Column(name = "actor_id", nullable = false, length = 64)
    private String actorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AuditStatus status;

    @Column(nullable = false, length = 1024)
    private String summary;

    @Column(length = 2048)
    private String reason;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;

    @Column(nullable = false)
    private Instant timestamp;

    @Column(name = "previous_event_hash", length = 64)
    private String previousEventHash;

    @Column(name = "event_hash", nullable = false, length = 64)
    private String eventHash;

    protected AuditEvent() {
        // JPA required
    }

    public AuditEvent(String eventId, Long incidentId, Long executionId, Long strategyId,
                      Long policyEvaluationId, AuditEventType eventType,
                      AuditEventCategory eventCategory, AuditActorType actorType,
                      String actorId, AuditSeverity severity, AuditStatus status,
                      String summary, String reason, String evidenceJson,
                      String metadataJson, Instant timestamp, String previousEventHash,
                      String eventHash) {
        this.eventId = eventId;
        this.incidentId = incidentId;
        this.executionId = executionId;
        this.strategyId = strategyId;
        this.policyEvaluationId = policyEvaluationId;
        this.eventType = eventType;
        this.eventCategory = eventCategory;
        this.actorType = actorType;
        this.actorId = actorId;
        this.severity = severity;
        this.status = status;
        this.summary = summary;
        this.reason = reason;
        this.evidenceJson = evidenceJson;
        this.metadataJson = metadataJson;
        this.timestamp = timestamp;
        this.previousEventHash = previousEventHash;
        this.eventHash = eventHash;
    }

    public Long getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Long getStrategyId() {
        return strategyId;
    }

    public Long getPolicyEvaluationId() {
        return policyEvaluationId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public AuditEventCategory getEventCategory() {
        return eventCategory;
    }

    public AuditActorType getActorType() {
        return actorType;
    }

    public String getActorId() {
        return actorId;
    }

    public AuditSeverity getSeverity() {
        return severity;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public String getSummary() {
        return summary;
    }

    public String getReason() {
        return reason;
    }

    public String getEvidenceJson() {
        return evidenceJson;
    }

    public String getMetadataJson() {
        return metadataJson;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getPreviousEventHash() {
        return previousEventHash;
    }

    public String getEventHash() {
        return eventHash;
    }

    // For testing/tampering verification tests only
    public void mutateSummaryForTamperTest(String tamperedSummary) {
        this.summary = tamperedSummary;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditEvent that = (AuditEvent) o;
        return Objects.equals(eventId, that.eventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventId);
    }
}
