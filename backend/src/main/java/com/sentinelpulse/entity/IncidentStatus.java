package com.sentinelpulse.entity;

/**
 * Lifecycle state of an incident. The detection engine creates incidents in
 * {@link #DETECTED} (or updates one already in an active state - see
 * {@code IncidentDetectionService}).
 */
public enum IncidentStatus {
    DETECTED,
    INVESTIGATING,
    RECOVERY_PENDING,
    RECOVERING,
    RESOLVED,
    ESCALATED
}
