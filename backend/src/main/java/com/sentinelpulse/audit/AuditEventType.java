package com.sentinelpulse.audit;

/**
 * Controlled event types for the Sentinel Audit Trail (Module 11).
 *
 * <p>Every lifecycle event across detection, diagnosis, investigation, strategy,
 * policy evaluation, and recovery execution is assigned a discrete, controlled type.
 */
public enum AuditEventType {
    // Incident Lifecycle
    INCIDENT_DETECTED,
    INCIDENT_UPDATED,
    INCIDENT_RESOLVED,

    // Financial Impact
    FINANCIAL_IMPACT_CALCULATED,

    // AI Diagnosis (Module 6)
    AI_DIAGNOSIS_REQUESTED,
    AI_DIAGNOSIS_COMPLETED,
    AI_DIAGNOSIS_FAILED,

    // Root-Cause Investigation (Module 7)
    INVESTIGATION_REQUESTED,
    INVESTIGATION_COMPLETED,
    INVESTIGATION_FAILED,

    // Recovery Strategy Proposal (Module 8)
    RECOVERY_STRATEGY_PROPOSED,
    RECOVERY_STRATEGY_REJECTED,
    RECOVERY_STRATEGY_SUPERSEDED,

    // Policy Evaluation & Gate Decisions (Module 9)
    POLICY_EVALUATED,
    POLICY_APPROVED,
    POLICY_APPROVED_WITH_LIMITS,
    POLICY_HUMAN_REVIEW,
    POLICY_REJECTED,

    // Recovery Execution Simulator (Module 10)
    RECOVERY_EXECUTION_REQUESTED,
    RECOVERY_EXECUTION_BLOCKED,
    RECOVERY_EXECUTION_STARTED,
    RECOVERY_ATTEMPTED,
    RECOVERY_SUCCEEDED,
    RECOVERY_FAILED,
    RECOVERY_STOPPED,
    RECOVERY_COMPLETED,
    EXECUTION_IDEMPOTENT_REUSE,

    // System
    SYSTEM_ERROR
}
