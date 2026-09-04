package com.sentinelpulse.dto;

import java.time.Instant;

/**
 * Response DTO for cryptographic audit chain verification.
 */
public record AuditVerificationResponse(
        Long incidentId,
        boolean valid,
        int eventCount,
        Instant firstEventTimestamp,
        Instant lastEventTimestamp,
        Long brokenEventId,
        String message
) {}
