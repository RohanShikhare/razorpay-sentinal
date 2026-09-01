package com.sentinelpulse.controller;

import com.sentinelpulse.dto.SimulationResetResponse;
import com.sentinelpulse.dto.SimulationRunRequest;
import com.sentinelpulse.dto.SimulationRunResponse;
import com.sentinelpulse.dto.SimulationStatusResponse;
import com.sentinelpulse.service.IncidentSimulationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the Incident Simulator. Contains no business logic -
 * everything delegates to {@link IncidentSimulationService}.
 *
 * These endpoints simulate payment degradation; they never claim an
 * incident was "detected". That is the future Monitoring & Incident
 * Detection Engine's responsibility.
 */
@RestController
@RequestMapping("/api/v1/simulations")
public class SimulationController {

    private final IncidentSimulationService incidentSimulationService;

    public SimulationController(IncidentSimulationService incidentSimulationService) {
        this.incidentSimulationService = incidentSimulationService;
    }

    @PostMapping("/run")
    public SimulationRunResponse run(@Valid @RequestBody SimulationRunRequest request) {
        return incidentSimulationService.runScenario(request.scenario());
    }

    @PostMapping("/reset")
    public SimulationResetResponse reset() {
        return incidentSimulationService.reset();
    }

    @GetMapping("/status")
    public SimulationStatusResponse status() {
        return incidentSimulationService.getStatus();
    }
}
