package com.sentinelpulse.controller;

import com.sentinelpulse.dto.IncidentInvestigationResponse;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.service.IncidentInvestigationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing deep root-cause investigation endpoints for payment incidents.
 *
 * All investigations are advisory and diagnostic. AI evaluates competing hypotheses
 * against deterministic telemetry facts without modifying payments or triggering recovery.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentInvestigationController {

    private final IncidentInvestigationService incidentInvestigationService;

    public IncidentInvestigationController(IncidentInvestigationService incidentInvestigationService) {
        this.incidentInvestigationService = incidentInvestigationService;
    }

    /**
     * Triggers an in-depth root-cause investigation for the specified incident.
     */
    @PostMapping("/{id}/investigate")
    public ResponseEntity<IncidentInvestigationResponse> investigate(@PathVariable("id") Long incidentId) {
        IncidentInvestigationResponse response = incidentInvestigationService.investigateIncident(incidentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the latest persisted root-cause investigation for the specified incident, if available.
     * Does NOT trigger a new AI investigation run.
     */
    @GetMapping("/{id}/investigation")
    public ResponseEntity<IncidentInvestigationResponse> getLatestInvestigation(@PathVariable("id") Long incidentId) {
        return incidentInvestigationService.getLatestInvestigation(incidentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No root-cause investigation found for incident: " + incidentId));
    }
}
