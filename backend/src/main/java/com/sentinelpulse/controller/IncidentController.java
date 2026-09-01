package com.sentinelpulse.controller;

import com.sentinelpulse.dto.IncidentDetailResponse;
import com.sentinelpulse.dto.IncidentDto;
import com.sentinelpulse.monitoring.IncidentDetectionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only REST endpoints for incidents detected by the Monitoring &
 * Incident Detection Engine. No update endpoints yet - those belong to the
 * future AI investigation and recovery modules.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentDetectionService incidentDetectionService;

    public IncidentController(IncidentDetectionService incidentDetectionService) {
        this.incidentDetectionService = incidentDetectionService;
    }

    @GetMapping
    public Page<IncidentDto> list(@PageableDefault(size = 20) Pageable pageable) {
        return incidentDetectionService.listIncidents(pageable);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse detail(@PathVariable Long id) {
        return incidentDetectionService.getIncidentDetail(id);
    }
}
