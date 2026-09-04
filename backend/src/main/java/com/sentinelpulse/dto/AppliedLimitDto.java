package com.sentinelpulse.dto;

/**
 * Details of a deterministic constraint or limit applied by the Policy Engine to a proposed recovery strategy.
 */
public record AppliedLimitDto(
        String parameter,
        String proposedValue,
        String enforcedValue,
        String reason
) {
}
