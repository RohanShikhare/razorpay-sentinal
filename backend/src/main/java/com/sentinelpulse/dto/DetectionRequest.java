package com.sentinelpulse.dto;

import jakarta.validation.constraints.Min;

/**
 * Request body for POST /api/v1/monitoring/detect. Both fields are
 * optional - defaults come from {@code MonitoringProperties}.
 */
public record DetectionRequest(

        @Min(value = 1, message = "windowMinutes must be at least 1")
        Integer windowMinutes

) {
}
