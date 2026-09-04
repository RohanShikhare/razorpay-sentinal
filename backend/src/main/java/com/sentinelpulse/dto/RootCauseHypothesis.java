package com.sentinelpulse.dto;

import java.util.List;

/**
 * Represents a competing root-cause hypothesis evaluated during deep investigation.
 */
public record RootCauseHypothesis(
        String hypothesis,
        double confidence,
        List<String> supportingEvidence,
        List<String> contradictingEvidence,
        String affectedSegment,
        String verdict
) {
}
