package com.sentinelpulse.controller;

import com.sentinelpulse.dto.FinancialBreakdownResponse;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.dto.IncidentDetailResponse;
import com.sentinelpulse.dto.IncidentDto;
import com.sentinelpulse.dto.IncidentEvidenceDto;
import com.sentinelpulse.financial.FinancialImpactService;
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
 * Incident Detection Engine, plus the deterministic financial-impact views
 * built on top of them (Module 4). No update endpoints yet - those belong
 * to the future AI investigation and recovery modules.
 */
@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private final IncidentDetectionService incidentDetectionService;
    private final FinancialImpactService financialImpactService;

    public IncidentController(IncidentDetectionService incidentDetectionService,
                               FinancialImpactService financialImpactService) {
        this.incidentDetectionService = incidentDetectionService;
        this.financialImpactService = financialImpactService;
    }

    @GetMapping
    public Page<IncidentDto> list(@PageableDefault(size = 20) Pageable pageable) {
        return incidentDetectionService.listIncidents(pageable);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse detail(@PathVariable Long id) {
        return incidentDetectionService.getIncidentDetail(id);
    }

    @GetMapping("/{id}/financial-impact")
    public FinancialImpactResponse financialImpact(@PathVariable("id") Long incidentId) {
        return financialImpactService.getFinancialImpact(incidentId);
    }

    @GetMapping("/{id}/financial-impact/breakdown")
    public FinancialBreakdownResponse financialImpactBreakdown(@PathVariable("id") Long incidentId) {
        return financialImpactService.getFinancialBreakdown(incidentId);
    }

    @GetMapping("/{id}/evidence")
    public IncidentEvidenceDto evidence(@PathVariable("id") Long incidentId) {
        return financialImpactService.getEvidence(incidentId);
    }
}

