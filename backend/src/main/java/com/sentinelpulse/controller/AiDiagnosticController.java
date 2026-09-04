package com.sentinelpulse.controller;

import com.sentinelpulse.dto.AiDiagnosisResponse;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.service.AiDiagnosticService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing AI diagnostic investigation endpoints for payment incidents.
 *
 * All diagnoses are advisory. AI never executes recovery actions or modifies deterministic financial calculations.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class AiDiagnosticController {

    private final AiDiagnosticService aiDiagnosticService;

    public AiDiagnosticController(AiDiagnosticService aiDiagnosticService) {
        this.aiDiagnosticService = aiDiagnosticService;
    }

    /**
     * Triggers a new AI diagnosis on the specified incident using deterministic evidence.
     */
    @PostMapping("/{id}/diagnose")
    public ResponseEntity<AiDiagnosisResponse> diagnose(@PathVariable("id") Long incidentId) {
        AiDiagnosisResponse response = aiDiagnosticService.diagnoseIncident(incidentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the latest persisted AI diagnosis for the specified incident, if available.
     * Does NOT trigger a new AI diagnosis run.
     */
    @GetMapping("/{id}/diagnosis")
    public ResponseEntity<AiDiagnosisResponse> getLatestDiagnosis(@PathVariable("id") Long incidentId) {
        return aiDiagnosticService.getLatestDiagnosis(incidentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No AI diagnosis found for incident: " + incidentId));
    }
}
