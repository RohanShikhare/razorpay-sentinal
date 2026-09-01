package com.sentinelpulse.controller;

import com.sentinelpulse.dto.DetectionRequest;
import com.sentinelpulse.dto.DetectionResponse;
import com.sentinelpulse.dto.MonitoringSummaryResponse;
import com.sentinelpulse.monitoring.IncidentDetectionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the deterministic Monitoring & Incident Detection
 * Engine. Contains no business logic itself - everything delegates to
 * {@link IncidentDetectionService}.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
public class MonitoringController {

    private final IncidentDetectionService incidentDetectionService;

    public MonitoringController(IncidentDetectionService incidentDetectionService) {
        this.incidentDetectionService = incidentDetectionService;
    }

    @PostMapping("/detect")
    public DetectionResponse detect(@Valid @RequestBody(required = false) DetectionRequest request) {
        Integer windowMinutes = request != null ? request.windowMinutes() : null;
        return incidentDetectionService.detect(windowMinutes);
    }

    @GetMapping("/summary")
    public MonitoringSummaryResponse summary(@RequestParam(required = false) Integer windowMinutes) {
        return incidentDetectionService.getSummary(windowMinutes);
    }
}
