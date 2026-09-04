package com.sentinelpulse.dto;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;

/**
 * Alternative recovery strategy evaluated and ranked alongside the recommended strategy.
 */
public record AlternativeStrategyDto(
        RecoveryStrategyType strategy,
        int rank,
        String reason,
        double expectedRecoveryPercentage,
        RiskLevel riskLevel
) {
}
