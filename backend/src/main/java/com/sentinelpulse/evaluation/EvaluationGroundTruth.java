package com.sentinelpulse.evaluation;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.policy.PolicyDecision;

import java.util.List;

/**
 * Known synthetic ground truth for an evaluation incident.
 *
 * <p>Ground truth is formulated strictly from the controlled scenario definition and is
 * completely independent from any AI outputs or runtime deductions.
 */
public record EvaluationGroundTruth(
        EvaluationScenarioType scenarioType,
        boolean incidentExpected,
        String expectedTargetBank,
        PaymentMethod expectedTargetPaymentMethod,
        String expectedTargetMerchantId,
        List<String> expectedRootCauseKeywords,
        List<RecoveryStrategyType> expectedSafeStrategies,
        PolicyDecision expectedPolicyDecision,
        boolean expectedExecutionAllowed,
        String description
) {
}
