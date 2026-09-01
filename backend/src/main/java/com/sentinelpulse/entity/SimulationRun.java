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

import java.time.Instant;

/**
 * Metadata about one run of the Incident Simulator.
 *
 * This is deliberately a lightweight simulation record, not a detected
 * incident - it says "the simulator applied this scenario to the data",
 * never "the system detected an incident". Incident detection and
 * persistence belong to a later module.
 *
 * At most one row should be {@code active = true} at a time; {@link
 * com.sentinelpulse.service.IncidentSimulationService} enforces that by
 * resetting any active run before starting a new one.
 */
@Entity
@Table(
        name = "simulation_runs",
        indexes = {
                @Index(name = "idx_simulation_runs_active", columnList = "active")
        }
)
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private SimulationScenario scenario;

    @Column(nullable = false)
    private Instant startedAt;

    private Instant endedAt;

    @Column(nullable = false)
    private boolean active;

    @Column(length = 16)
    private String targetBank;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private PaymentMethod targetPaymentMethod;

    @Column(length = 32)
    private String targetMerchantId;

    @Column(nullable = false)
    private long affectedTransactions;

    @Column(nullable = false)
    private long modifiedTransactions;

    @Column(nullable = false)
    private double failureRateBefore;

    @Column(nullable = false)
    private double failureRateAfter;

    protected SimulationRun() {
        // required by JPA
    }

    public SimulationRun(SimulationScenario scenario, Instant startedAt, String targetBank,
                          PaymentMethod targetPaymentMethod, String targetMerchantId,
                          long affectedTransactions, long modifiedTransactions,
                          double failureRateBefore, double failureRateAfter) {
        this.scenario = scenario;
        this.startedAt = startedAt;
        this.active = true;
        this.targetBank = targetBank;
        this.targetPaymentMethod = targetPaymentMethod;
        this.targetMerchantId = targetMerchantId;
        this.affectedTransactions = affectedTransactions;
        this.modifiedTransactions = modifiedTransactions;
        this.failureRateBefore = failureRateBefore;
        this.failureRateAfter = failureRateAfter;
    }

    public Long getId() {
        return id;
    }

    public SimulationScenario getScenario() {
        return scenario;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public boolean isActive() {
        return active;
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

    public long getAffectedTransactions() {
        return affectedTransactions;
    }

    public long getModifiedTransactions() {
        return modifiedTransactions;
    }

    public double getFailureRateBefore() {
        return failureRateBefore;
    }

    public double getFailureRateAfter() {
        return failureRateAfter;
    }

    public void markEnded(Instant endedAt) {
        this.active = false;
        this.endedAt = endedAt;
    }
}
