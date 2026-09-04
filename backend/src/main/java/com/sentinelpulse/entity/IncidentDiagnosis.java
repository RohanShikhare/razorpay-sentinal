package com.sentinelpulse.entity;

import com.sentinelpulse.ai.ControlledAction;
import com.sentinelpulse.ai.DiagnosisStatus;
import com.sentinelpulse.ai.RiskLevel;
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
 * Persisted record of an AI diagnosis run for an incident.
 * Contains the model's advisory interpretation, risk assessment, and recommended action.
 */
@Entity
@Table(
        name = "incident_diagnoses",
        indexes = {
                @Index(name = "idx_diagnoses_incident_id", columnList = "incident_id"),
                @Index(name = "idx_diagnoses_created_at", columnList = "created_at")
        }
)
public class IncidentDiagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false, length = 64)
    private String provider;

    @Column(nullable = false, length = 64)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DiagnosisStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "incident_type", length = 32)
    private IncidentType incidentType;

    @Column(name = "root_cause", length = 512)
    private String rootCause;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Column(columnDefinition = "TEXT")
    private String evidence;

    @Column(name = "affected_segment", length = 255)
    private String affectedSegment;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_action", length = 32)
    private ControlledAction recommendedAction;

    @Column(name = "estimated_recovery_percentage")
    private Double estimatedRecoveryPercentage;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 16)
    private RiskLevel riskLevel;

    @Column(name = "requires_human_approval")
    private Boolean requiresHumanApproval;

    @Column(name = "raw_response", columnDefinition = "TEXT")
    private String rawResponse;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    protected IncidentDiagnosis() {
    }

    public IncidentDiagnosis(Long incidentId, Instant createdAt, String provider, String model,
                             DiagnosisStatus status, IncidentType incidentType, String rootCause,
                             Double confidence, String reasoning, String evidence, String affectedSegment,
                             ControlledAction recommendedAction, Double estimatedRecoveryPercentage,
                             RiskLevel riskLevel, Boolean requiresHumanApproval, String rawResponse,
                             String errorMessage) {
        this.incidentId = incidentId;
        this.createdAt = createdAt;
        this.provider = provider;
        this.model = model;
        this.status = status;
        this.incidentType = incidentType;
        this.rootCause = rootCause;
        this.confidence = confidence;
        this.reasoning = reasoning;
        this.evidence = evidence;
        this.affectedSegment = affectedSegment;
        this.recommendedAction = recommendedAction;
        this.estimatedRecoveryPercentage = estimatedRecoveryPercentage;
        this.riskLevel = riskLevel;
        this.requiresHumanApproval = requiresHumanApproval;
        this.rawResponse = rawResponse;
        this.errorMessage = errorMessage;
    }

    public static IncidentDiagnosis failed(Long incidentId, String provider, String model,
                                           DiagnosisStatus status, String errorMessage, String rawResponse) {
        return new IncidentDiagnosis(
                incidentId, Instant.now(), provider, model, status,
                null, null, null, null, null, null,
                null, null, null, null, rawResponse, errorMessage
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

    public String getProvider() {
        return provider;
    }

    public String getModel() {
        return model;
    }

    public DiagnosisStatus getStatus() {
        return status;
    }

    public IncidentType getIncidentType() {
        return incidentType;
    }

    public String getRootCause() {
        return rootCause;
    }

    public Double getConfidence() {
        return confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getEvidence() {
        return evidence;
    }

    public String getAffectedSegment() {
        return affectedSegment;
    }

    public ControlledAction getRecommendedAction() {
        return recommendedAction;
    }

    public Double getEstimatedRecoveryPercentage() {
        return estimatedRecoveryPercentage;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Boolean getRequiresHumanApproval() {
        return requiresHumanApproval;
    }

    public String getRawResponse() {
        return rawResponse;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
