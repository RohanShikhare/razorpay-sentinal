package com.sentinelpulse.recovery;

/**
 * Lifecycle status of a recovery execution run (Module 10).
 */
public enum ExecutionStatus {
    REQUESTED,
    BLOCKED,
    RUNNING,
    COMPLETED,
    PARTIALLY_COMPLETED,
    STOPPED,
    FAILED
}
