package com.sentinelpulse.dto;

import java.util.List;

/**
 * Response body for GET /api/v1/incidents/{id}/financial-impact/breakdown.
 */
public record FinancialBreakdownResponse(
        Long incidentId,
        List<FinancialSegmentDto> byBank,
        List<FinancialSegmentDto> byPaymentMethod,
        List<FinancialSegmentDto> byErrorCode,
        List<FinancialSegmentDto> byMerchant
) {
}
