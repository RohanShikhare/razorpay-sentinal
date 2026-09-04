package com.sentinelpulse.dto;

import com.sentinelpulse.policy.PolicyCheckResult;
import com.sentinelpulse.policy.PolicyCheckSeverity;

/**
 * Result of an individual deterministic policy guardrail check.
 */
public record PolicyCheckDto(
        String policyId,
        String policyName,
        PolicyCheckResult result,
        PolicyCheckSeverity severity,
        String reason,
        String observedValue,
        String allowedValue
) {
    public static PolicyCheckDto pass(String policyId, String policyName, String reason, String observed) {
        return new PolicyCheckDto(policyId, policyName, PolicyCheckResult.PASS, PolicyCheckSeverity.INFO, reason, observed, "Satisfied");
    }

    public static PolicyCheckDto warn(String policyId, String policyName, PolicyCheckSeverity severity, String reason, String observed, String allowed) {
        return new PolicyCheckDto(policyId, policyName, PolicyCheckResult.WARN, severity, reason, observed, allowed);
    }

    public static PolicyCheckDto fail(String policyId, String policyName, PolicyCheckSeverity severity, String reason, String observed, String allowed) {
        return new PolicyCheckDto(policyId, policyName, PolicyCheckResult.FAIL, severity, reason, observed, allowed);
    }
}
