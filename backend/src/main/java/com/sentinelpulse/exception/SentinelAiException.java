package com.sentinelpulse.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an AI provider fails, times out, or produces an unparseable response.
 */
public class SentinelAiException extends SentinelException {

    public SentinelAiException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }

    public SentinelAiException(String message, Throwable cause) {
        super(message, HttpStatus.BAD_GATEWAY);
        initCause(cause);
    }
}
