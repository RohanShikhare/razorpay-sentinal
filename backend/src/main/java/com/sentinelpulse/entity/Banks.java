package com.sentinelpulse.entity;

import java.util.List;

/**
 * The set of banks synthetic payments can be routed through.
 *
 * Kept as plain string constants (not an enum) so the {@code bank} query
 * parameter on {@code GET /api/v1/payments} can match directly, e.g.
 * {@code ?bank=Bank%20X}, without a name-vs-label conversion.
 */
public final class Banks {

    public static final String BANK_A = "Bank A";
    public static final String BANK_B = "Bank B";
    public static final String BANK_C = "Bank C";
    public static final String BANK_X = "Bank X";
    public static final String BANK_Y = "Bank Y";

    public static final List<String> ALL = List.of(BANK_A, BANK_B, BANK_C, BANK_X, BANK_Y);

    private Banks() {
    }
}
