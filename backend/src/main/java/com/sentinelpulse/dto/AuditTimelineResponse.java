package com.sentinelpulse.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Response DTO returning the full chronological audit timeline for an incident or execution.
 */
public record AuditTimelineResponse(
        Long incidentId,
        Long executionId,
        int totalEvents,
        boolean chainValid,
        Instant firstEventTimestamp,
        Instant lastEventTimestamp,
        DecisionChainSummary decisionChain,
        List<AuditEventDto> events
) {
    public record DecisionChainSummary(
            boolean detected,
            boolean diagnosed,
            boolean investigated,
            boolean strategyProposed,
            boolean policyApproved,
            boolean recoveryExecuted,
            BigDecimal recoveredAmount,
            String currentStage
    ) {}
}
