package com.sentinelpulse.entity;

/**
 * Lifecycle state of an incident. Module 3 only ever creates incidents in
 * {@link #DETECTED} (or updates one already in an active state - see
 * {@code IncidentDetectionService}). The remaining states are here so the
 * schema doesn't need to change when later modules (AI investigation,
 * recovery, human escalation) start moving incidents through them.
 */
public enum IncidentStatus {
    DETECTED,
    INVESTIGATING,
    RECOVERY_PENDING,
    RECOVERING,
    RESOLVED,
    ESCALATED
}
