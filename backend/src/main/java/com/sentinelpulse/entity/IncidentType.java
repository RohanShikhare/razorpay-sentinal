package com.sentinelpulse.entity;

/**
 * What kind of payment degradation an incident represents, based on where
 * the detection engine found the strongest statistical signal.
 */
public enum IncidentType {

    /**
     * The failure-rate spike is concentrated in a bank, payment method, or
     * bank+payment-method segment (e.g. Bank X + UPI) - the segment shows a
     * materially larger deviation than the system as a whole.
     */
    PAYMENT_DEGRADATION,

    /**
     * The failure-rate spike is concentrated in a single merchant, with
     * banks and payment methods otherwise unremarkable.
     */
    MERCHANT_DEGRADATION,

    /**
     * The failure rate is elevated broadly, without strong concentration in
     * any single bank, payment method, or merchant segment.
     */
    SYSTEM_DEGRADATION
}
