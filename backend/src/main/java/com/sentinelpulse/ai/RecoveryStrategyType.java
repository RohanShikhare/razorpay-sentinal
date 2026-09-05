package com.sentinelpulse.ai;

/**
 * Controlled, bounded recovery strategy types that Sentinel can propose.
 * Arbitrary or unvetted actions are strictly prohibited.
 * All strategy proposals are advisory and subject to downstream deterministic policy evaluation.
 */
public enum RecoveryStrategyType {
    IMMEDIATE_RETRY,
    DELAYED_RETRY,
    ALTERNATE_PAYMENT_METHOD,
    RETRY_WITH_BACKOFF,
    MONITOR_ONLY,
    HUMAN_REVIEW,
    NO_ACTION
}
