package com.sentinelpulse.dto;

import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.recovery.RecoveryOutcome;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Details of an individual synthetic transaction retry attempt.
 */
public record RecoveryExecutionAttemptDto(
        Long attemptId,
        Long paymentId,
        int attemptNumber,
        Instant attemptedAt,
        RecoveryOutcome outcome,
        BigDecimal amount,
        String failureReason,
        RecoveryStrategyType strategyType,
        Integer simulatedDelaySeconds
) {
}
