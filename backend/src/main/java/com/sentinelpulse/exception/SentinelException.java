package com.sentinelpulse.exception;

import org.springframework.http.HttpStatus;

/**
 * Base unchecked exception for domain-level failures.
 *
 * Later modules (monitoring, AI, policy, recovery, audit) should extend this
 * rather than throwing raw exceptions, so all domain errors flow through the
 * same {@link GlobalExceptionHandler} and produce a consistent response.
 */
public class SentinelException extends RuntimeException {

    private final HttpStatus status;

    public SentinelException(String message) {
        this(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public SentinelException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
