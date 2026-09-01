package com.sentinelpulse.entity;

/**
 * Why a payment failed. {@link #NONE} is used for payments that did not
 * fail (SUCCESS, PENDING) - a failure reason is never forced onto a
 * non-failed payment.
 */
public enum ErrorCode {
    NONE,
    BANK_TIMEOUT,
    INSUFFICIENT_FUNDS,
    NETWORK_ERROR,
    ISSUER_DECLINED,
    RATE_LIMIT,
    UNKNOWN
}
