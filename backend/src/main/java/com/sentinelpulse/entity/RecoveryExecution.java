package com.sentinelpulse.entity;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.StoppingReason;
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
 * Persisted entity recording a bounded recovery execution simulation run (Module 10).
 *
 * <p>Strict Safety Invariant:
 * Module 10 executes ONLY within bounds explicitly approved by Module 9 Policy Gate.
 * This entity captures both the authoritative baseline, AI proposal, policy limits,
 * and the actual simulated recovery outcome.
 */
@Entity
@Table(name = "recovery_executions", indexes = {
        @Index(name = "idx_rec_exec_incident_id", columnList = "incident_id"),
        @Index(name = "idx_rec_exec_strategy_id", columnList = "strategy_id"),
        @Index(name = "idx_rec_exec_policy_eval_id", columnList = "policy_evaluation_id"),
        @Index(name = "idx_rec_exec_started_at", columnList = "started_at")
})
public class RecoveryExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "strategy_id", nullable = false)
    private Long strategyId;

    @Column(name = "policy_evaluation_id", nullable = false)
    private Long policyEvaluationId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ExecutionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false, length = 32)
    private RecoveryStrategyType strategyType;

    @Column(name = "eligible_transactions", nullable = false)
    private Long eligibleTransactions;

    @Column(name = "attempted_transactions", nullable = false)
    private Long attemptedTransactions;

    @Column(name = "successful_recoveries", nullable = false)
    private Long successfulRecoveries;

    @Column(name = "failed_recoveries", nullable = false)
    private Long failedRecoveries;

    @Column(name = "skipped_transactions", nullable = false)
    private Long skippedTransactions;

    @Column(name = "recovered_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal recoveredAmount;

    @Column(name = "attempted_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal attemptedAmount;

    @Column(name = "remaining_recoverable_amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal remainingRecoverableAmount;

    @Column(name = "authoritative_revenue_at_risk", precision = 18, scale = 2)
    private BigDecimal authoritativeRevenueAtRisk;

    @Column(name = "authoritative_recoverable_amount", precision = 18, scale = 2)
    private BigDecimal authoritativeRecoverableAmount;

    @Column(name = "expected_recovery_percentage")
    private Double expectedRecoveryPercentage;

    @Column(name = "recovery_rate", nullable = false)
    private Double recoveryRate;

    @Column(name = "financial_recovery_rate", nullable = false)
    private Double financialRecoveryRate;

    @Enumerated(EnumType.STRING)
    @Column(name = "stopping_reason", nullable = false, length = 64)
    private StoppingReason stoppingReason;

    @Column(name = "stopping_details", columnDefinition = "TEXT")
    private String stoppingDetails;

    @Column(name = "execution_allowed", nullable = false)
    private Boolean executionAllowed;

    @Column(name = "applied_retry_limit")
    private Integer appliedRetryLimit;

    @Column(name = "applied_retry_window")
    private Integer appliedRetryWindow;

    @Column(name = "applied_backoff")
    private Integer appliedBackoff;

    @Column(name = "simulation_version", length = 32, nullable = false)
    private String simulationVersion;

    @Column(name = "audit_metadata_json", columnDefinition = "TEXT")
    private String auditMetadataJson;

    protected RecoveryExecution() {
    }

    public RecoveryExecution(Long incidentId,
                             Long strategyId,
                             Long policyEvaluationId,
                             Instant startedAt,
                             Instant completedAt,
                             ExecutionStatus status,
                             RecoveryStrategyType strategyType,
                             Long eligibleTransactions,
                             Long attemptedTransactions,
                             Long successfulRecoveries,
                             Long failedRecoveries,
                             Long skippedTransactions,
                             BigDecimal recoveredAmount,
                             BigDecimal attemptedAmount,
                             BigDecimal remainingRecoverableAmount,
                             BigDecimal authoritativeRevenueAtRisk,
                             BigDecimal authoritativeRecoverableAmount,
                             Double expectedRecoveryPercentage,
                             Double recoveryRate,
                             Double financialRecoveryRate,
                             StoppingReason stoppingReason,
                             String stoppingDetails,
                             Boolean executionAllowed,
                             Integer appliedRetryLimit,
                             Integer appliedRetryWindow,
                             Integer appliedBackoff,
                             String simulationVersion,
                             String auditMetadataJson) {
        this.incidentId = incidentId;
        this.strategyId = strategyId;
        this.policyEvaluationId = policyEvaluationId;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.status = status;
        this.strategyType = strategyType;
        this.eligibleTransactions = eligibleTransactions;
        this.attemptedTransactions = attemptedTransactions;
        this.successfulRecoveries = successfulRecoveries;
        this.failedRecoveries = failedRecoveries;
        this.skippedTransactions = skippedTransactions;
        this.recoveredAmount = recoveredAmount;
        this.attemptedAmount = attemptedAmount;
        this.remainingRecoverableAmount = remainingRecoverableAmount;
        this.authoritativeRevenueAtRisk = authoritativeRevenueAtRisk;
        this.authoritativeRecoverableAmount = authoritativeRecoverableAmount;
        this.expectedRecoveryPercentage = expectedRecoveryPercentage;
        this.recoveryRate = recoveryRate;
        this.financialRecoveryRate = financialRecoveryRate;
        this.stoppingReason = stoppingReason;
        this.stoppingDetails = stoppingDetails;
        this.executionAllowed = executionAllowed;
        this.appliedRetryLimit = appliedRetryLimit;
        this.appliedRetryWindow = appliedRetryWindow;
        this.appliedBackoff = appliedBackoff;
        this.simulationVersion = simulationVersion;
        this.auditMetadataJson = auditMetadataJson;
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

    public Long getPolicyEvaluationId() {
        return policyEvaluationId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public ExecutionStatus getStatus() {
        return status;
    }

    public void setStatus(ExecutionStatus status) {
        this.status = status;
    }

    public RecoveryStrategyType getStrategyType() {
        return strategyType;
    }

    public Long getEligibleTransactions() {
        return eligibleTransactions;
    }

    public Long getAttemptedTransactions() {
        return attemptedTransactions;
    }

    public Long getSuccessfulRecoveries() {
        return successfulRecoveries;
    }

    public Long getFailedRecoveries() {
        return failedRecoveries;
    }

    public Long getSkippedTransactions() {
        return skippedTransactions;
    }

    public BigDecimal getRecoveredAmount() {
        return recoveredAmount;
    }

    public BigDecimal getAttemptedAmount() {
        return attemptedAmount;
    }

    public BigDecimal getRemainingRecoverableAmount() {
        return remainingRecoverableAmount;
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

    public Double getRecoveryRate() {
        return recoveryRate;
    }

    public Double getFinancialRecoveryRate() {
        return financialRecoveryRate;
    }

    public StoppingReason getStoppingReason() {
        return stoppingReason;
    }

    public String getStoppingDetails() {
        return stoppingDetails;
    }

    public Boolean getExecutionAllowed() {
        return executionAllowed;
    }

    public Integer getAppliedRetryLimit() {
        return appliedRetryLimit;
    }

    public Integer getAppliedRetryWindow() {
        return appliedRetryWindow;
    }

    public Integer getAppliedBackoff() {
        return appliedBackoff;
    }

    public String getSimulationVersion() {
        return simulationVersion;
    }

    public String getAuditMetadataJson() {
        return auditMetadataJson;
    }
}
