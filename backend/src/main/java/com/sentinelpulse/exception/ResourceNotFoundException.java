package com.sentinelpulse.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested entity (payment, incident, recovery record, etc.)
 * cannot be found. Shared across all services for uniform 404 handling.
 */
public class ResourceNotFoundException extends SentinelException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
