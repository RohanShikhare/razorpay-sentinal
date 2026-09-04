package com.sentinelpulse.dto;

import com.sentinelpulse.evaluation.EvaluationScenarioType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Request payload for executing a multi-scenario evaluation benchmark.
 */
public record EvaluationRunRequest(
        @Min(value = 1, message = "scenarioCount must be at least 1")
        @Max(value = 100, message = "scenarioCount cannot exceed 100")
        Integer scenarioCount,

        Long seed,

        Boolean includeBaseline,

        List<EvaluationScenarioType> scenarios
) {
    public EvaluationRunRequest {
        if (scenarioCount == null) {
            scenarioCount = 20;
        }
        if (seed == null) {
            seed = 42L;
        }
        if (includeBaseline == null) {
            includeBaseline = true;
        }
    }
}
