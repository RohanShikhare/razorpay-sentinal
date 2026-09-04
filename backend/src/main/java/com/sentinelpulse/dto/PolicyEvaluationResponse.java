package com.sentinelpulse.dto;

import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.policy.PolicyDecision;

import java.time.Instant;
import java.util.List;

/**
 * Structured response representing the deterministic outcome of a Policy Engine gate evaluation.
 */
public record PolicyEvaluationResponse(
        Long evaluationId,
        Long incidentId,
        Long strategyId,
        Instant evaluatedAt,
        String evaluatorVersion,
        PolicyDecision decision,
        boolean overallAllowed,
        boolean executionAllowed,
        boolean requiredHumanApproval,
        RiskLevel riskLevel,
        double policyScore,
        String summary,
        String executionBlockedReason,
        String nextStep,
        List<PolicyCheckDto> policyChecks,
        List<AppliedLimitDto> appliedLimits,
        List<String> violations,
        List<String> warnings,
        String advisoryDisclaimer
) {
    public static final String DEFAULT_POLICY_DISCLAIMER =
            "Policy Gate is deterministic. AI cannot override this decision. Recovery execution is strictly blocked unless the Policy Gate explicitly permits it.";
}
