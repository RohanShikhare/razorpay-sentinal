package com.sentinelpulse.dto;

import com.sentinelpulse.safety.SafetyScenarioType;

/**
 * Metadata descriptor for a safety failure scenario.
 */
public record SafetyScenarioDto(
        SafetyScenarioType type,
        String name,
        String category,
        String description,
        String safetyProperty,
        String expectedBehavior
) {
}
