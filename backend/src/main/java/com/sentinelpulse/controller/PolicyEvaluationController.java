package com.sentinelpulse.controller;

import com.sentinelpulse.dto.PolicyEvaluationResponse;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.service.PolicyEvaluationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing deterministic policy and safety guardrail evaluation endpoints (Module 9).
 *
 * <p>Strict Architectural Boundary:
 * Module 9 evaluates whether proposed recovery actions are legally and operationally permissible.
 * This controller executes ZERO AI/LLM calls and executes ZERO money movement / retry operations.
 * Execution is delegated strictly to Module 10.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class PolicyEvaluationController {

    private final PolicyEvaluationService policyEvaluationService;

    public PolicyEvaluationController(PolicyEvaluationService policyEvaluationService) {
        this.policyEvaluationService = policyEvaluationService;
    }

    /**
     * Evaluates the latest proposed recovery strategy against deterministic safety policies and guardrails.
     * Persists the evaluation record with versioned audit metadata.
     */
    @PostMapping("/{id}/policy-evaluate")
    public ResponseEntity<PolicyEvaluationResponse> evaluatePolicy(@PathVariable("id") Long incidentId) {
        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incidentId);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves the latest persisted policy evaluation for the specified incident.
     * Guaranteed to be idempotent: does not invoke AI, regenerate strategies, or alter state.
     */
    @GetMapping("/{id}/policy-evaluation")
    public ResponseEntity<PolicyEvaluationResponse> getLatestPolicyEvaluation(@PathVariable("id") Long incidentId) {
        return policyEvaluationService.getLatestEvaluation(incidentId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResourceNotFoundException("No policy evaluation found for incident: " + incidentId));
    }
}
