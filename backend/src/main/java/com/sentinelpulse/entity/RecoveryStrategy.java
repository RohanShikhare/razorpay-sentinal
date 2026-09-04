package com.sentinelpulse.entity;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Entity persisting recovery strategy proposals for payment incidents.
 * Stores both authoritative financial anchors and bounded strategy estimates.
 */
@Entity
@Table(name = "recovery_strategies", indexes = {
        @Index(name = "idx_recovery_strategy_incident_id", columnList = "incident_id"),
        @Index(name = "idx_recovery_strategy_created_at", columnList = "created_at")
})
public class RecoveryStrategy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "investigation_id")
    private Long investigationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private StrategyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_strategy", nullable = false, length = 64)
    private RecoveryStrategyType selectedStrategy;

    @Column(name = "strategy_rank", nullable = false)
    private Integer strategyRank;

    @Column(name = "rationale", columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "confidence")
    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 32)
    private RiskLevel riskLevel;

    @Column(name = "affected_segment", length = 128)
    private String affectedSegment;

    @Column(name = "authoritative_revenue_at_risk", precision = 15, scale = 2)
    private BigDecimal authoritativeRevenueAtRisk;

    @Column(name = "authoritative_recoverable_amount", precision = 15, scale = 2)
    private BigDecimal authoritativeRecoverableAmount;

    @Column(name = "expected_recovery_percentage")
    private Double expectedRecoveryPercentage;

    @Column(name = "estimated_recovery_amount", precision = 15, scale = 2)
    private BigDecimal estimatedRecoveryAmount;

    @Column(name = "estimated_affected_transaction_count")
    private Long estimatedAffectedTransactionCount;

    @Column(name = "stopping_conditions_json", columnDefinition = "TEXT")
    private String stoppingConditionsJson;

    @Column(name = "requires_human_approval")
    private Boolean requiresHumanApproval;

    @Column(name = "alternatives_json", columnDefinition = "TEXT")
    private String alternativesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 32)
    private StrategySource source;

    @Column(name = "raw_ai_response", columnDefinition = "TEXT")
    private String rawAiResponse;

    @Column(name = "status_reason", columnDefinition = "TEXT")
    private String statusReason;

    protected RecoveryStrategy() {
    }

    public RecoveryStrategy(Long incidentId,
                            Long investigationId,
                            Instant createdAt,
                            StrategyStatus status,
                            RecoveryStrategyType selectedStrategy,
                            Integer strategyRank,
                            String rationale,
                            Double confidence,
                            RiskLevel riskLevel,
                            String affectedSegment,
                            BigDecimal authoritativeRevenueAtRisk,
                            BigDecimal authoritativeRecoverableAmount,
                            Double expectedRecoveryPercentage,
                            BigDecimal estimatedRecoveryAmount,
                            Long estimatedAffectedTransactionCount,
                            String stoppingConditionsJson,
                            Boolean requiresHumanApproval,
                            String alternativesJson,
                            StrategySource source,
                            String rawAiResponse,
                            String statusReason) {
        this.incidentId = incidentId;
        this.investigationId = investigationId;
        this.createdAt = createdAt;
        this.status = status;
        this.selectedStrategy = selectedStrategy;
        this.strategyRank = strategyRank;
        this.rationale = rationale;
        this.confidence = confidence;
        this.riskLevel = riskLevel;
        this.affectedSegment = affectedSegment;
        this.authoritativeRevenueAtRisk = authoritativeRevenueAtRisk;
        this.authoritativeRecoverableAmount = authoritativeRecoverableAmount;
        this.expectedRecoveryPercentage = expectedRecoveryPercentage;
        this.estimatedRecoveryAmount = estimatedRecoveryAmount;
        this.estimatedAffectedTransactionCount = estimatedAffectedTransactionCount;
        this.stoppingConditionsJson = stoppingConditionsJson;
        this.requiresHumanApproval = requiresHumanApproval;
        this.alternativesJson = alternativesJson;
        this.source = source;
        this.rawAiResponse = rawAiResponse;
        this.statusReason = statusReason;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getInvestigationId() {
        return investigationId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public StrategyStatus getStatus() {
        return status;
    }

    public void setStatus(StrategyStatus status) {
        this.status = status;
    }

    public RecoveryStrategyType getSelectedStrategy() {
        return selectedStrategy;
    }

    public Integer getStrategyRank() {
        return strategyRank;
    }

    public String getRationale() {
        return rationale;
    }

    public Double getConfidence() {
        return confidence;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public String getAffectedSegment() {
        return affectedSegment;
    }

    public BigDecimal getAuthoritativeRevenueAtRisk() {
        return authoritativeRevenueAtRisk;
    }

    public BigDecimal getAuthoritativeRecoverableAmount() {
        return authoritativeRecoverableAmount;
    }

    public Double getExpectedRecoveryPercentage() {
        return expectedRecoveryPercentage;
    }

    public BigDecimal getEstimatedRecoveryAmount() {
        return estimatedRecoveryAmount;
    }

    public Long getEstimatedAffectedTransactionCount() {
        return estimatedAffectedTransactionCount;
    }

    public String getStoppingConditionsJson() {
        return stoppingConditionsJson;
    }

    public Boolean getRequiresHumanApproval() {
        return requiresHumanApproval;
    }

    public String getAlternativesJson() {
        return alternativesJson;
    }

    public StrategySource getSource() {
        return source;
    }

    public String getRawAiResponse() {
        return rawAiResponse;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }
}
