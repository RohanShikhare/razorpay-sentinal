package com.sentinelpulse.ai;

/**
 * Lifecycle status of a generated recovery strategy proposal.
 */
public enum StrategyStatus {
    PROPOSED,
    REJECTED,
    SUPERSEDED,
    FAILED,
    INVALID_RESPONSE
}
