package com.sentinelpulse.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Persisted record of an evaluation benchmark run (Module 13).
 */
@Entity
@Table(name = "evaluation_runs", indexes = {
        @Index(name = "idx_eval_run_id", columnList = "run_id", unique = true),
        @Index(name = "idx_eval_started_at", columnList = "started_at")
})
public class EvaluationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true, length = 64)
    private String runId;

    @Column(name = "evaluator_version", nullable = false, length = 32)
    private String evaluatorVersion;

    @Column(name = "seed", nullable = false)
    private Long seed;

    @Column(name = "scenario_count", nullable = false)
    private Integer scenarioCount;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "total_revenue_at_risk", precision = 19, scale = 2)
    private BigDecimal totalRevenueAtRisk;

    @Column(name = "total_recoverable_revenue", precision = 19, scale = 2)
    private BigDecimal totalRecoverableRevenue;

    @Column(name = "total_recovered_revenue", precision = 19, scale = 2)
    private BigDecimal totalRecoveredRevenue;

    @Column(name = "baseline_recovered_revenue", precision = 19, scale = 2)
    private BigDecimal baselineRecoveredRevenue;

    @Column(name = "financial_recovery_rate")
    private Double financialRecoveryRate;

    @Column(name = "detection_precision")
    private Double detectionPrecision;

    @Column(name = "detection_recall")
    private Double detectionRecall;

    @Column(name = "detection_f1")
    private Double detectionF1;

    @Column(name = "diagnosis_accuracy")
    private Double diagnosisAccuracy;

    @Column(name = "policy_compliance_rate")
    private Double policyComplianceRate;

    @Column(name = "safety_score")
    private Integer safetyScore;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "summary_json", columnDefinition = "TEXT")
    private String summaryJson;

    @Column(name = "incidents_json", columnDefinition = "TEXT")
    private String incidentsJson;

    public EvaluationRun() {}

    public EvaluationRun(String runId, String evaluatorVersion, Long seed, Integer scenarioCount,
                         Instant startedAt, Instant completedAt, String status,
                         BigDecimal totalRevenueAtRisk, BigDecimal totalRecoverableRevenue,
                         BigDecimal totalRecoveredRevenue, BigDecimal baselineRecoveredRevenue,
                         Double financialRecoveryRate, Double detectionPrecision, Double detectionRecall,
                         Double detectionF1, Double diagnosisAccuracy, Double policyComplianceRate,
                         Integer safetyScore, Double overallScore, String summaryJson, String incidentsJson) {
        this.runId = runId;
        this.evaluatorVersion = evaluatorVersion;
        this.seed = seed;
        this.scenarioCount = scenarioCount;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.status = status;
        this.totalRevenueAtRisk = totalRevenueAtRisk;
        this.totalRecoverableRevenue = totalRecoverableRevenue;
        this.totalRecoveredRevenue = totalRecoveredRevenue;
        this.baselineRecoveredRevenue = baselineRecoveredRevenue;
        this.financialRecoveryRate = financialRecoveryRate;
        this.detectionPrecision = detectionPrecision;
        this.detectionRecall = detectionRecall;
        this.detectionF1 = detectionF1;
        this.diagnosisAccuracy = diagnosisAccuracy;
        this.policyComplianceRate = policyComplianceRate;
        this.safetyScore = safetyScore;
        this.overallScore = overallScore;
        this.summaryJson = summaryJson;
        this.incidentsJson = incidentsJson;
    }

    public Long getId() {
        return id;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getEvaluatorVersion() {
        return evaluatorVersion;
    }

    public void setEvaluatorVersion(String evaluatorVersion) {
        this.evaluatorVersion = evaluatorVersion;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public Integer getScenarioCount() {
        return scenarioCount;
    }

    public void setScenarioCount(Integer scenarioCount) {
        this.scenarioCount = scenarioCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getTotalRevenueAtRisk() {
        return totalRevenueAtRisk;
    }

    public void setTotalRevenueAtRisk(BigDecimal totalRevenueAtRisk) {
        this.totalRevenueAtRisk = totalRevenueAtRisk;
    }

    public BigDecimal getTotalRecoverableRevenue() {
        return totalRecoverableRevenue;
    }

    public void setTotalRecoverableRevenue(BigDecimal totalRecoverableRevenue) {
        this.totalRecoverableRevenue = totalRecoverableRevenue;
    }

    public BigDecimal getTotalRecoveredRevenue() {
        return totalRecoveredRevenue;
    }

    public void setTotalRecoveredRevenue(BigDecimal totalRecoveredRevenue) {
        this.totalRecoveredRevenue = totalRecoveredRevenue;
    }

    public BigDecimal getBaselineRecoveredRevenue() {
        return baselineRecoveredRevenue;
    }

    public void setBaselineRecoveredRevenue(BigDecimal baselineRecoveredRevenue) {
        this.baselineRecoveredRevenue = baselineRecoveredRevenue;
    }

    public Double getFinancialRecoveryRate() {
        return financialRecoveryRate;
    }

    public void setFinancialRecoveryRate(Double financialRecoveryRate) {
        this.financialRecoveryRate = financialRecoveryRate;
    }

    public Double getDetectionPrecision() {
        return detectionPrecision;
    }

    public void setDetectionPrecision(Double detectionPrecision) {
        this.detectionPrecision = detectionPrecision;
    }

    public Double getDetectionRecall() {
        return detectionRecall;
    }

    public void setDetectionRecall(Double detectionRecall) {
        this.detectionRecall = detectionRecall;
    }

    public Double getDetectionF1() {
        return detectionF1;
    }

    public void setDetectionF1(Double detectionF1) {
        this.detectionF1 = detectionF1;
    }

    public Double getDiagnosisAccuracy() {
        return diagnosisAccuracy;
    }

    public void setDiagnosisAccuracy(Double diagnosisAccuracy) {
        this.diagnosisAccuracy = diagnosisAccuracy;
    }

    public Double getPolicyComplianceRate() {
        return policyComplianceRate;
    }

    public void setPolicyComplianceRate(Double policyComplianceRate) {
        this.policyComplianceRate = policyComplianceRate;
    }

    public Integer getSafetyScore() {
        return safetyScore;
    }

    public void setSafetyScore(Integer safetyScore) {
        this.safetyScore = safetyScore;
    }

    public Double getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Double overallScore) {
        this.overallScore = overallScore;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getIncidentsJson() {
        return incidentsJson;
    }

    public void setIncidentsJson(String incidentsJson) {
        this.incidentsJson = incidentsJson;
    }
}
