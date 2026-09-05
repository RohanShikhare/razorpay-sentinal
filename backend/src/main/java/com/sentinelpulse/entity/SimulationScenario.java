package com.sentinelpulse.entity;

/**
 * A deterministic payment-degradation scenario the Incident Simulator can
 * apply to the synthetic dataset.
 *
 * The simulator only ever creates these conditions - it never declares that
 * an incident has been detected. That judgment belongs to the Monitoring &
 * Incident Detection Engine, which will read the same
 * {@code payments} table this scenario modifies.
 */
public enum SimulationScenario {

    /**
     * The primary demo scenario: Bank X's UPI traffic develops a sharply
     * elevated, heavily BANK_TIMEOUT-flavored failure rate, while every
     * other bank/payment-method combination is left alone. Designed so a
     * future root-cause engine can cleanly attribute the failure to
     * "Bank X + UPI".
     */
    BANK_X_UPI_DEGRADATION,

    /**
     * One (auto-selected, high-volume) merchant develops an elevated
     * failure rate with a non-bank-flavored error mix, while every other
     * merchant stays normal. Designed to test whether root-cause analysis
     * incorrectly blames a bank when the evidence actually points at the
     * merchant.
     */
    MERCHANT_SPECIFIC_DEGRADATION,

    /**
     * A small, broadly-spread increase in failures across the whole
     * dataset - ordinary operational noise, not concentrated on any single
     * bank, payment method, or merchant. A negative/control case for
     * future incident detection.
     */
    NORMAL_RANDOM_FAILURES
}
