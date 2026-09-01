package com.sentinelpulse.dto;

import com.sentinelpulse.entity.SimulationScenario;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for POST /api/v1/simulations/run.
 *
 * Intentionally just the scenario - the primary requirement is that the
 * predefined scenarios work reliably, so severity/percentage knobs are not
 * exposed here to avoid overengineering a small demo surface.
 */
public record SimulationRunRequest(
        @NotNull(message = "scenario is required")
        SimulationScenario scenario
) {
}
