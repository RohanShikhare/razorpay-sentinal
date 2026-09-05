package com.sentinelpulse.policy;

/**
 * Immutable deterministic decisions rendered by the Sentinel Policy Engine.
 *
 * <p>AI proposes strategies. Policy decides. Execution obeys policy.
 */
public enum PolicyDecision {
    /**
     * The strategy satisfies all mandatory safety policies and can proceed directly to execution.
     */
    APPROVED,

    /**
     * The strategy is permitted to execute, but only within explicitly clamped/enforced deterministic limits.
     */
    APPROVED_WITH_LIMITS,

    /**
     * The strategy may be sound, but autonomous execution is blocked; explicit human commander authorization is required.
     */
    HUMAN_REVIEW,

    /**
     * The strategy violates one or more mandatory safety policies and is strictly forbidden from executing.
     */
    REJECTED
}
