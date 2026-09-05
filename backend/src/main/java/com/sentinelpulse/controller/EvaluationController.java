package com.sentinelpulse.controller;

import com.sentinelpulse.dto.EvaluationRunRequest;
import com.sentinelpulse.dto.EvaluationRunResponse;
import com.sentinelpulse.dto.EvaluationScenarioDescriptorDto;
import com.sentinelpulse.dto.IncidentEvaluationResultDto;
import com.sentinelpulse.service.EvaluationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST Controller for the Evaluation & Benchmarking Engine.
 *
 * <p><strong>BENCHMARK / EVALUATION ONLY:</strong>
 * Runs reproducible synthetic benchmark experiments comparing Sentinel against ground truth
 * and baselines across financial, detection, diagnosis, strategy, policy, recovery, and safety metrics.
 */
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private static final Logger log = LoggerFactory.getLogger(EvaluationController.class);

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * Lists all available evaluation scenario definitions.
     */
    @GetMapping("/scenarios")
    public ResponseEntity<List<EvaluationScenarioDescriptorDto>> getScenarios() {
        return ResponseEntity.ok(evaluationService.getAvailableScenarios());
    }

    /**
     * Executes a deterministic multi-scenario evaluation benchmark run.
     */
    @PostMapping("/run")
    public ResponseEntity<EvaluationRunResponse> runEvaluation(@Valid @RequestBody(required = false) EvaluationRunRequest request) {
        if (request == null) {
            request = new EvaluationRunRequest(20, 42L, true, null);
        }
        log.info("REST: Executing evaluation benchmark run: scenarioCount={}, seed={}", request.scenarioCount(), request.seed());
        EvaluationRunResponse response = evaluationService.runEvaluation(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves an evaluation benchmark run by unique runId.
     */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<EvaluationRunResponse> getEvaluationRun(@PathVariable("runId") String runId) {
        log.info("REST: Fetching evaluation run [{}]", runId);
        return ResponseEntity.ok(evaluationService.getEvaluationRun(runId));
    }

    /**
     * Retrieves the most recently executed evaluation benchmark run.
     */
    @GetMapping("/latest")
    public ResponseEntity<EvaluationRunResponse> getLatestEvaluationRun() {
        log.info("REST: Fetching latest evaluation benchmark run");
        return ResponseEntity.ok(evaluationService.getLatestEvaluationRun());
    }

    /**
     * Retrieves incident-level results for a specific evaluation run.
     */
    @GetMapping("/runs/{runId}/incidents")
    public ResponseEntity<List<IncidentEvaluationResultDto>> getEvaluationIncidents(@PathVariable("runId") String runId) {
        log.info("REST: Fetching evaluation incident results for run [{}]", runId);
        return ResponseEntity.ok(evaluationService.getEvaluationIncidents(runId));
    }
}
