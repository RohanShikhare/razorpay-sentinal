package com.sentinelpulse.entity;

import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.policy.PolicyDecision;
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
 * Entity persisting deterministic Policy Gate evaluation decisions.
 * Serves as an immutable record of whether a strategy was approved, clamped, escalated, or rejected.
 */
@Entity
@Table(name = "policy_evaluations", indexes = {
        @Index(name = "idx_policy_eval_incident_id", columnList = "incident_id"),
        @Index(name = "idx_policy_eval_strategy_id", columnList = "strategy_id"),
        @Index(name = "idx_policy_eval_evaluated_at", columnList = "evaluated_at")
})
public class PolicyEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @Column(name = "evaluator_version", length = 32, nullable = false)
    private String evaluatorVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 32)
    private PolicyDecision decision;

    @Column(name = "overall_allowed", nullable = false)
    private Boolean overallAllowed;

    @Column(name = "execution_allowed", nullable = false)
    private Boolean executionAllowed;

    @Column(name = "required_human_approval", nullable = false)
    private Boolean requiredHumanApproval;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", length = 32)
    private RiskLevel riskLevel;

    @Column(name = "policy_score")
    private Double policyScore;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "execution_blocked_reason", columnDefinition = "TEXT")
    private String executionBlockedReason;

    @Column(name = "next_step", columnDefinition = "TEXT")
    private String nextStep;

    @Column(name = "policy_checks_json", columnDefinition = "TEXT")
    private String policyChecksJson;

    @Column(name = "applied_limits_json", columnDefinition = "TEXT")
    private String appliedLimitsJson;

    @Column(name = "violations_json", columnDefinition = "TEXT")
    private String violationsJson;

    @Column(name = "warnings_json", columnDefinition = "TEXT")
    private String warningsJson;

    protected PolicyEvaluation() {
    }

    public PolicyEvaluation(Long incidentId,
                            Long strategyId,
                            Instant evaluatedAt,
                            String evaluatorVersion,
                            PolicyDecision decision,
                            Boolean overallAllowed,
                            Boolean executionAllowed,
                            Boolean requiredHumanApproval,
                            RiskLevel riskLevel,
                            Double policyScore,
                            String summary,
                            String executionBlockedReason,
                            String nextStep,
                            String policyChecksJson,
                            String appliedLimitsJson,
                            String violationsJson,
                            String warningsJson) {
        this.incidentId = incidentId;
        this.strategyId = strategyId;
        this.evaluatedAt = evaluatedAt;
        this.evaluatorVersion = evaluatorVersion;
        this.decision = decision;
        this.overallAllowed = overallAllowed;
        this.executionAllowed = executionAllowed;
        this.requiredHumanApproval = requiredHumanApproval;
        this.riskLevel = riskLevel;
        this.policyScore = policyScore;
        this.summary = summary;
        this.executionBlockedReason = executionBlockedReason;
        this.nextStep = nextStep;
        this.policyChecksJson = policyChecksJson;
        this.appliedLimitsJson = appliedLimitsJson;
        this.violationsJson = violationsJson;
        this.warningsJson = warningsJson;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public Long getStrategyId() {
        return strategyId;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    public String getEvaluatorVersion() {
        return evaluatorVersion;
    }

    public PolicyDecision getDecision() {
        return decision;
    }

    public Boolean getOverallAllowed() {
        return overallAllowed;
    }

    public Boolean getExecutionAllowed() {
        return executionAllowed;
    }

    public Boolean getRequiredHumanApproval() {
        return requiredHumanApproval;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public Double getPolicyScore() {
        return policyScore;
    }

    public String getSummary() {
        return summary;
    }

    public String getExecutionBlockedReason() {
        return executionBlockedReason;
    }

    public String getNextStep() {
        return nextStep;
    }

    public String getPolicyChecksJson() {
        return policyChecksJson;
    }

    public String getAppliedLimitsJson() {
        return appliedLimitsJson;
    }

    public String getViolationsJson() {
        return violationsJson;
    }

    public String getWarningsJson() {
        return warningsJson;
    }
}
