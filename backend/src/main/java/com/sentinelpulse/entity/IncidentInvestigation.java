package com.sentinelpulse.entity;

import com.sentinelpulse.ai.InvestigationStatus;
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

/**
 * Entity persisting deep root-cause investigations of payment incidents.
 */
@Entity
@Table(name = "incident_investigations", indexes = {
        @Index(name = "idx_investigation_incident_id", columnList = "incident_id"),
        @Index(name = "idx_investigation_created_at", columnList = "created_at")
})
public class IncidentInvestigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InvestigationStatus status;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "model", length = 64)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", length = 64)
    private IncidentType incidentType;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "strongest_root_cause", columnDefinition = "TEXT")
    private String strongestRootCause;

    @Column(name = "overall_confidence")
    private Double overallConfidence;

    @Column(name = "affected_segment", length = 128)
    private String affectedSegment;

    @Column(name = "investigation_reasoning", columnDefinition = "TEXT")
    private String investigationReasoning;

    @Column(name = "uncertainty", columnDefinition = "TEXT")
    private String uncertainty;

    @Column(name = "requires_human_review")
    private Boolean requiresHumanReview;

    @Column(name = "hypotheses_json", columnDefinition = "TEXT")
    private String hypothesesJson;

    @Column(name = "supporting_evidence_json", columnDefinition = "TEXT")
    private String supportingEvidenceJson;

    @Column(name = "contradicting_evidence_json", columnDefinition = "TEXT")
    private String contradictingEvidenceJson;

    @Column(name = "raw_ai_response", columnDefinition = "TEXT")
    private String rawAiResponse;

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    protected IncidentInvestigation() {
    }

    public IncidentInvestigation(Long incidentId,
                                 Instant createdAt,
                                 Instant completedAt,
                                 InvestigationStatus status,
                                 String provider,
                                 String model,
                                 IncidentType incidentType,
                                 String summary,
                                 String strongestRootCause,
                                 Double overallConfidence,
                                 String affectedSegment,
                                 String investigationReasoning,
                                 String uncertainty,
                                 Boolean requiresHumanReview,
                                 String hypothesesJson,
                                 String supportingEvidenceJson,
                                 String contradictingEvidenceJson,
                                 String rawAiResponse,
                                 String statusReason) {
        this.incidentId = incidentId;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
        this.status = status;
        this.provider = provider;
        this.model = model;
        this.incidentType = incidentType;
        this.summary = summary;
        this.strongestRootCause = strongestRootCause;
        this.overallConfidence = overallConfidence;
        this.affectedSegment = affectedSegment;
        this.investigationReasoning = investigationReasoning;
        this.uncertainty = uncertainty;
        this.requiresHumanReview = requiresHumanReview;
        this.hypothesesJson = hypothesesJson;
        this.supportingEvidenceJson = supportingEvidenceJson;
        this.contradictingEvidenceJson = contradictingEvidenceJson;
        this.rawAiResponse = rawAiResponse;
        this.statusReason = statusReason;
    }

    public static IncidentInvestigation failed(Long incidentId,
                                               String provider,
                                               String model,
                                               InvestigationStatus status,
                                               String errorMessage,
                                               String rawResponse) {
        Instant now = Instant.now();
        return new IncidentInvestigation(
                incidentId,
                now,
                now,
                status,
                provider,
                model,
                null,
                null,
                null,
                0.0,
                null,
                null,
                null,
                true,
                "[]",
                "[]",
                "[]",
                rawResponse,
                errorMessage
        );
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public InvestigationStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public String getSummary() {
        return summary;
    }

    public String getStrongestRootCause() {
        return strongestRootCause;
    }

    public Double getOverallConfidence() {
        return overallConfidence;
    }

    public String getAffectedSegment() {
        return affectedSegment;
    }

    public String getInvestigationReasoning() {
        return investigationReasoning;
    }

    public String getUncertainty() {
        return uncertainty;
    }

    public Boolean getRequiresHumanReview() {
        return requiresHumanReview;
    }

    public String getHypothesesJson() {
        return hypothesesJson;
    }

    public String getSupportingEvidenceJson() {
        return supportingEvidenceJson;
    }

    public String getContradictingEvidenceJson() {
        return contradictingEvidenceJson;
    }

    public String getRawAiResponse() {
        return rawAiResponse;
    }

    public String getStatusReason() {
        return statusReason;
    }
}
