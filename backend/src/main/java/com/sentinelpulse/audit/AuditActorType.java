package com.sentinelpulse.audit;

/**
 * Controlled actor taxonomy for audit events.
 *
 * <p>Clearly separates AI advisory outputs from deterministic policy decisions,
 * automated execution actions, system telemetry, and human operations.
 */
public enum AuditActorType {
    SYSTEM,
    AI,
    POLICY_ENGINE,
    RECOVERY_ENGINE,
    HUMAN_OPERATOR,
    USER
}
