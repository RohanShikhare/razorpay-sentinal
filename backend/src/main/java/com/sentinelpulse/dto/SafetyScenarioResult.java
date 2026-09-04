package com.sentinelpulse.dto;

import com.sentinelpulse.safety.SafetyScenarioStatus;
import com.sentinelpulse.safety.SafetyScenarioType;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Structured outcome resulting from the execution of a safety scenario (Module 12).
 */
public record SafetyScenarioResult(
        SafetyScenarioType scenario,
        SafetyScenarioStatus status,
        boolean passed,
        String name,
        String category,
        String expectedBehavior,
        String observedBehavior,
        String safetyProperty,
        Long incidentId,
        Long executionId,
        String policyDecision,
        Boolean executionAllowed,
        String recoveryStatus,
        BigDecimal recoveredAmount,
        String stoppingReason,
        Integer auditEventCount,
        Boolean auditVerificationValid,
        String details,
        Instant executedAt
) {
    public static SafetyScenarioResult success(SafetyScenarioType scenario,
                                               String name,
                                               String category,
                                               String expectedBehavior,
                                               String observedBehavior,
                                               String safetyProperty,
                                               Long incidentId,
                                               Long executionId,
                                               String policyDecision,
                                               Boolean executionAllowed,
                                               String recoveryStatus,
                                               BigDecimal recoveredAmount,
                                               String stoppingReason,
                                               Integer auditEventCount,
                                               Boolean auditVerificationValid,
                                               String details) {
        return new SafetyScenarioResult(
                scenario,
                SafetyScenarioStatus.PASSED,
                true,
                name,
                category,
                expectedBehavior,
                observedBehavior,
                safetyProperty,
                incidentId,
                executionId,
                policyDecision,
                executionAllowed,
                recoveryStatus,
                recoveredAmount,
                stoppingReason,
                auditEventCount,
                auditVerificationValid,
                details,
                Instant.now()
        );
    }

    public static SafetyScenarioResult failure(SafetyScenarioType scenario,
                                               String name,
                                               String category,
                                               String expectedBehavior,
                                               String observedBehavior,
                                               String safetyProperty,
                                               Long incidentId,
                                               String details) {
        return new SafetyScenarioResult(
                scenario,
                SafetyScenarioStatus.FAILED,
                false,
                name,
                category,
                expectedBehavior,
                observedBehavior,
                safetyProperty,
                incidentId,
                null,
                null,
                false,
                null,
                BigDecimal.ZERO,
                null,
                null,
                false,
                details,
                Instant.now()
        );
    }

    public static SafetyScenarioResult error(SafetyScenarioType scenario,
                                             String name,
                                             String category,
                                             String expectedBehavior,
                                             String errorMessage) {
        return new SafetyScenarioResult(
                scenario,
                SafetyScenarioStatus.ERROR,
                false,
                name,
                category,
                expectedBehavior,
                "Unexpected test execution error: " + errorMessage,
                "System must not crash",
                null,
                null,
                null,
                false,
                null,
                BigDecimal.ZERO,
                null,
                null,
                false,
                errorMessage,
                Instant.now()
        );
    }
}
