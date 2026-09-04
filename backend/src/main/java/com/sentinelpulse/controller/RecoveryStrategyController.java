package com.sentinelpulse.controller;

import com.sentinelpulse.dto.RecoveryStrategyResponse;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.service.RecoveryStrategyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing recovery strategy proposal endpoints for payment incidents.
 *
 * <p>Strict Module Boundary:
 * All recovery strategies produced are advisory proposals with bounded stopping conditions.
 * This controller does NOT execute retries, approve policies, or alter payment records.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class RecoveryStrategyController {

    private final RecoveryStrategyService recoveryStrategyService;

    public RecoveryStrategyController(RecoveryStrategyService recoveryStrategyService) {
        this.recoveryStrategyService = recoveryStrategyService;
    }

    /**
     * Formulates and persists a bounded recovery strategy proposal for the specified incident.
     */
    @PostMapping("/{id}/recovery-strategy")
    public ResponseEntity<RecoveryStrategyResponse> generateRecoveryStrategy(@PathVariable("id") Long incidentId) {
        RecoveryStrategyResponse response = recoveryStrategyService.generateStrategy(incidentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the latest persisted recovery strategy proposal for the specified incident.
     * Does NOT regenerate or invoke AI.
     */
    @GetMapping("/{id}/recovery-strategy")
    public ResponseEntity<RecoveryStrategyResponse> getLatestRecoveryStrategy(@PathVariable("id") Long incidentId) {
        return recoveryStrategyService.getLatestStrategy(incidentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No recovery strategy found for incident: " + incidentId));
    }
}
