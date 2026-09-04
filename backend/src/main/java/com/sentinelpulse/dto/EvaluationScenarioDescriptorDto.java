package com.sentinelpulse.dto;

import com.sentinelpulse.evaluation.EvaluationScenarioType;

import java.util.List;

/**
 * Metadata descriptor for an evaluation scenario.
 */
public record EvaluationScenarioDescriptorDto(
        EvaluationScenarioType scenario,
        String name,
        String description,
        String category,
        boolean incidentExpected,
        List<String> expectedSafeStrategies,
        String expectedPolicyBehavior,
        String expectedRootCausePattern
) {
}
