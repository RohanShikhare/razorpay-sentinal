package com.sentinelpulse.ai;

/**
 * Bounded, discrete actions that the AI diagnostic agent can recommend.
 * Free-text or uncontrolled actions are strictly disallowed.
 *
 * All actions are advisory and subject to downstream deterministic policy validation.
 */
public enum ControlledAction {
    IMMEDIATE_RETRY,
    DELAYED_RETRY,
    ALTERNATE_PAYMENT_METHOD,
    MONITOR,
    HUMAN_REVIEW,
    NO_ACTION
}
