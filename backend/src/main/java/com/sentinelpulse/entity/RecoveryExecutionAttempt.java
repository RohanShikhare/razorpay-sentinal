package com.sentinelpulse.entity;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.recovery.RecoveryOutcome;
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
 * Persisted entity representing an individual transaction-level recovery attempt (Module 10).
 *
 * <p>Preserves source payment immutability while tracking granular retry timeline steps.
 */
@Entity
@Table(name = "recovery_execution_attempts", indexes = {
        @Index(name = "idx_rec_attempt_exec_id", columnList = "execution_id"),
        @Index(name = "idx_rec_attempt_payment_id", columnList = "payment_id"),
        @Index(name = "idx_rec_attempted_at", columnList = "attempted_at")
})
public class RecoveryExecutionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false)
    private Long executionId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private RecoveryOutcome outcome;

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy_type", nullable = false, length = 32)
    private RecoveryStrategyType strategyType;

    @Column(name = "simulated_delay_seconds")
    private Integer simulatedDelaySeconds;

    protected RecoveryExecutionAttempt() {
    }

    public RecoveryExecutionAttempt(Long executionId,
                                    Long paymentId,
                                    Integer attemptNumber,
                                    Instant attemptedAt,
                                    RecoveryOutcome outcome,
                                    BigDecimal amount,
                                    String failureReason,
                                    RecoveryStrategyType strategyType,
                                    Integer simulatedDelaySeconds) {
        this.executionId = executionId;
        this.paymentId = paymentId;
        this.attemptNumber = attemptNumber;
        this.attemptedAt = attemptedAt;
        this.outcome = outcome;
        this.amount = amount;
        this.failureReason = failureReason;
        this.strategyType = strategyType;
        this.simulatedDelaySeconds = simulatedDelaySeconds;
    }

    public Long getId() {
        return id;
    }

    public Long getExecutionId() {
        return executionId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }

    public RecoveryOutcome getOutcome() {
        return outcome;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public RecoveryStrategyType getStrategyType() {
        return strategyType;
    }

    public Integer getSimulatedDelaySeconds() {
        return simulatedDelaySeconds;
    }
}
