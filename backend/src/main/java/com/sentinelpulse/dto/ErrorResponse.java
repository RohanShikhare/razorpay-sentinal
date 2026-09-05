package com.sentinelpulse.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard shape for every error returned by the API.
 *
 * Kept as a plain record so domain services (payments, AI, policy, recovery)
 * can throw domain exceptions and rely on one consistent error contract.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public ErrorResponse(int status, String error, String message, String path, List<String> details) {
        this(Instant.now(), status, error, message, path, details);
    }

    public ErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, List.of());
    }
}
