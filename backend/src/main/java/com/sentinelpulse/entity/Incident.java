package com.sentinelpulse.entity;

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
 * A payment incident found by the deterministic Monitoring & Incident
 * Detection Engine.
 *
 * Detection and diagnosis are deliberately separate: this entity only ever
 * records *what was statistically observed* (which segment, how much the
 * failure rate moved, how many transactions and how much revenue are
 * implicated). {@link #rootCause} stays null here - assigning it is the
 * AI investigation engine's job, not the deterministic detector's.
 */
@Entity
@Table(
        name = "incidents",
        indexes = {
                @Index(name = "idx_incidents_status", columnList = "status"),
                @Index(name = "idx_incidents_detected_at", columnList = "detected_at")
        }
)
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IncidentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncidentSeverity severity;

    @Column(nullable = false)
    private Instant detectedAt;

    @Column(nullable = false)
    private Instant startTime;

    private Instant endTime;

    @Column(nullable = false)
    private long affectedTransactions;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal affectedTransactionValue;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal failedTransactionValue;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal revenueAtRisk;

    /**
     * Deterministic detection-confidence score, 0-100. Reflects the
     * strength of the statistical signal (see {@code IncidentDetectionService}
     * for the formula) - not an AI confidence score.
     */
    @Column(nullable = false)
    private double confidence;

    /**
     * Null until the AI investigation engine assigns one.
     * The deterministic detector never guesses a root cause.
     */
    @Column(length = 512)
    private String rootCause;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private IncidentStatus status;

    @Column(nullable = false)
    private double baselineFailureRate;

    @Column(nullable = false)
    private double currentFailureRate;

    @Column(nullable = false)
    private double failureRateIncrease;

    @Column(length = 16)
    private String targetBank;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PaymentMethod targetPaymentMethod;

    @Column(length = 32)
    private String targetMerchantId;

    @Column(nullable = false)
    private int monitoringWindowMinutes;

    protected Incident() {
        // required by JPA
    }

    public Incident(IncidentType type, IncidentSeverity severity, Instant detectedAt, Instant startTime,
                     long affectedTransactions, BigDecimal affectedTransactionValue, BigDecimal failedTransactionValue,
                     BigDecimal revenueAtRisk, double confidence, IncidentStatus status,
                     double baselineFailureRate, double currentFailureRate, double failureRateIncrease,
                     String targetBank, PaymentMethod targetPaymentMethod, String targetMerchantId,
                     int monitoringWindowMinutes) {
        this.type = type;
        this.severity = severity;
        this.detectedAt = detectedAt;
        this.startTime = startTime;
        this.affectedTransactions = affectedTransactions;
        this.affectedTransactionValue = affectedTransactionValue;
        this.failedTransactionValue = failedTransactionValue;
        this.revenueAtRisk = revenueAtRisk;
        this.confidence = confidence;
        this.rootCause = null;
        this.status = status;
        this.baselineFailureRate = baselineFailureRate;
        this.currentFailureRate = currentFailureRate;
        this.failureRateIncrease = failureRateIncrease;
        this.targetBank = targetBank;
        this.targetPaymentMethod = targetPaymentMethod;
        this.targetMerchantId = targetMerchantId;
        this.monitoringWindowMinutes = monitoringWindowMinutes;
    }

    public Long getId() {
        return id;
    }

    public IncidentType getType() {
        return type;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public long getAffectedTransactions() {
        return affectedTransactions;
    }

    public BigDecimal getAffectedTransactionValue() {
        return affectedTransactionValue;
    }

    public BigDecimal getFailedTransactionValue() {
        return failedTransactionValue;
    }

    public BigDecimal getRevenueAtRisk() {
        return revenueAtRisk;
    }

    public double getConfidence() {
        return confidence;
    }

    public String getRootCause() {
        return rootCause;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public double getBaselineFailureRate() {
        return baselineFailureRate;
    }

    public double getCurrentFailureRate() {
        return currentFailureRate;
    }

    public double getFailureRateIncrease() {
        return failureRateIncrease;
    }

    public String getTargetBank() {
        return targetBank;
    }

    public PaymentMethod getTargetPaymentMethod() {
        return targetPaymentMethod;
    }

    public String getTargetMerchantId() {
        return targetMerchantId;
    }

    public int getMonitoringWindowMinutes() {
        return monitoringWindowMinutes;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    /**
     * Refreshes an existing incident with a newer detection reading of the
     * same underlying condition, instead of creating a duplicate row. See
     * {@code IncidentDetectionService} for how "same underlying condition"
     * is determined.
     */
    public void refresh(IncidentSeverity severity, Instant detectedAt, long affectedTransactions,
                         BigDecimal affectedTransactionValue, BigDecimal failedTransactionValue,
                         BigDecimal revenueAtRisk, double confidence,
                         double baselineFailureRate, double currentFailureRate, double failureRateIncrease) {
        this.severity = severity;
        this.detectedAt = detectedAt;
        this.affectedTransactions = affectedTransactions;
        this.affectedTransactionValue = affectedTransactionValue;
        this.failedTransactionValue = failedTransactionValue;
        this.revenueAtRisk = revenueAtRisk;
        this.confidence = confidence;
        this.baselineFailureRate = baselineFailureRate;
        this.currentFailureRate = currentFailureRate;
        this.failureRateIncrease = failureRateIncrease;
    }
}
