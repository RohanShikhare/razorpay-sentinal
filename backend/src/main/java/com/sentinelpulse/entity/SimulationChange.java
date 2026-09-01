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

/**
 * The pre-simulation status/error code of one {@link Payment} that a
 * {@link SimulationRun} modified.
 *
 * This is what makes {@code POST /api/v1/simulations/reset} possible
 * without regenerating the whole dataset: for every payment a scenario
 * touches, its original values are recorded here first, then restored on
 * reset. Rows are deleted once their run has been reset, so this table only
 * ever holds changes for the currently active run.
 */
@Entity
@Table(
        name = "simulation_changes",
        indexes = {
                @Index(name = "idx_simulation_changes_run", columnList = "simulation_run_id")
        }
)
public class SimulationChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "simulation_run_id", nullable = false)
    private Long simulationRunId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ErrorCode previousErrorCode;

    protected SimulationChange() {
        // required by JPA
    }

    public SimulationChange(Long simulationRunId, Long paymentId,
                             PaymentStatus previousStatus, ErrorCode previousErrorCode) {
        this.simulationRunId = simulationRunId;
        this.paymentId = paymentId;
        this.previousStatus = previousStatus;
        this.previousErrorCode = previousErrorCode;
    }

    public Long getId() {
        return id;
    }

    public Long getSimulationRunId() {
        return simulationRunId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public PaymentStatus getPreviousStatus() {
        return previousStatus;
    }

    public ErrorCode getPreviousErrorCode() {
        return previousErrorCode;
    }
}
