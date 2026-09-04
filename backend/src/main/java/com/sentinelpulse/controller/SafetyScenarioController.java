package com.sentinelpulse.controller;

import com.sentinelpulse.dto.SafetyScenarioDto;
import com.sentinelpulse.dto.SafetyScenarioResult;
import com.sentinelpulse.safety.SafetyScenarioType;
import com.sentinelpulse.service.SafetyScenarioService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for the Sentinel Safety & Failure Lab (Module 12).
 *
 * <p><strong>DEMO / TEST / SIMULATION ONLY:</strong>
 * Exercises controlled synthetic failure scenarios demonstrating that Sentinel fails closed,
 * respects deterministic policy bounds, halts on circuit breakers, and detects audit tampering.
 * No real payment gateways or live financial transactions are invoked.
 */
@RestController
@RequestMapping("/api/v1/safety")
public class SafetyScenarioController {

    private static final Logger log = LoggerFactory.getLogger(SafetyScenarioController.class);

    private final SafetyScenarioService safetyScenarioService;

    public SafetyScenarioController(SafetyScenarioService safetyScenarioService) {
        this.safetyScenarioService = safetyScenarioService;
    }

    /**
     * Lists all available controlled safety failure scenarios.
     */
    @GetMapping("/scenarios")
    public ResponseEntity<List<SafetyScenarioDto>> getScenarios() {
        return ResponseEntity.ok(safetyScenarioService.getScenarioDefinitions());
    }

    /**
     * Runs a single controlled safety failure scenario.
     */
    @PostMapping("/scenarios/{scenario}/run")
    public ResponseEntity<SafetyScenarioResult> runScenario(@PathVariable("scenario") SafetyScenarioType scenario) {
        log.info("REST: Running safety scenario [{}]", scenario);
        SafetyScenarioResult result = safetyScenarioService.runScenario(scenario);
        return ResponseEntity.ok(result);
    }

    /**
     * Runs all 12 controlled safety failure scenarios in batch.
     */
    @PostMapping("/scenarios/run-all")
    public ResponseEntity<List<SafetyScenarioResult>> runAllScenarios() {
        log.info("REST: Running all safety scenarios in batch");
        List<SafetyScenarioResult> results = safetyScenarioService.runAllScenarios();
        return ResponseEntity.ok(results);
    }
}
