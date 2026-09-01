package com.sentinelpulse.dto;

import java.util.List;

/**
 * Response body for GET /api/v1/incidents/{id}.
 *
 * {@code topAffectedSegments} is recomputed live (from current payment
 * data, scoped to the incident's original detection window) rather than
 * frozen at detection time, so the detail view always reflects the current
 * state of the data behind the incident.
 */
public record IncidentDetailResponse(
        IncidentDto incident,
        List<SegmentBreakdownDto> topAffectedSegments
) {
}
