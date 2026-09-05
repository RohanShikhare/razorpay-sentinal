package com.sentinelpulse.controller;

import com.sentinelpulse.dto.RecoveryExecutionResponse;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.service.RecoveryExecutionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing bounded recovery execution simulation endpoints.
 *
 * <p>Strict Safety Boundary:
 * The Recovery Execution Simulator executes strictly what the Policy Gate permits.
 * This controller simulates retries on synthetic payment data only.
 * No money movement, no external gateway calls, and no real customer transactions are executed.
 */
@RestController
@RequestMapping("/api/v1")
public class RecoveryExecutionController {

    private final RecoveryExecutionService recoveryExecutionService;

    public RecoveryExecutionController(RecoveryExecutionService recoveryExecutionService) {
        this.recoveryExecutionService = recoveryExecutionService;
    }

    /**
     * Executes bounded recovery simulation for an incident obeying Policy Gate limits.
     */
    @PostMapping("/incidents/{id}/recovery-execute")
    public ResponseEntity<RecoveryExecutionResponse> executeRecovery(@PathVariable("id") Long incidentId) {
        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incidentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the latest persisted recovery execution for the specified incident.
     * Guaranteed idempotent: does not trigger execution, mutate state, or call AI.
     */
    @GetMapping("/incidents/{id}/recovery-execution")
    public ResponseEntity<RecoveryExecutionResponse> getLatestExecution(@PathVariable("id") Long incidentId) {
        return recoveryExecutionService.getLatestExecution(incidentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No recovery execution found for incident: " + incidentId));
    }

    /**
     * Retrieves full execution details by execution ID including attempt timeline.
     */
    @GetMapping("/recovery-executions/{executionId}")
    public ResponseEntity<RecoveryExecutionResponse> getExecutionById(@PathVariable("executionId") Long executionId) {
        RecoveryExecutionResponse response = recoveryExecutionService.getExecutionById(executionId);
        return ResponseEntity.ok(response);
    }
}
