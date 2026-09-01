package com.sentinelpulse.exception;

import org.springframework.http.HttpStatus;

/**
 * Thrown when a requested entity (payment, incident, recovery record, etc.)
 * cannot be found. Introduced now so later modules have a ready-made
 * exception instead of each module inventing its own.
 */
public class ResourceNotFoundException extends SentinelException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
