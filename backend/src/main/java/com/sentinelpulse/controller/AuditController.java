package com.sentinelpulse.controller;

import com.sentinelpulse.dto.AuditTimelineResponse;
import com.sentinelpulse.dto.AuditVerificationResponse;
import com.sentinelpulse.service.AuditEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller for Sentinel Audit Trail & Decision History.
 *
 * <p>Endpoints provide read-only chronological timelines and cryptographic hash chain verification.
 * No GET endpoint ever mutates audit state or domain data.
 */
@RestController
@RequestMapping("/api/v1")
public class AuditController {

    private final AuditEventService auditEventService;

    public AuditController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    /**
     * Retrieves the complete chronological audit timeline for an incident.
     */
    @GetMapping("/incidents/{incidentId}/audit")
    public ResponseEntity<AuditTimelineResponse> getIncidentAuditTimeline(@PathVariable Long incidentId) {
        AuditTimelineResponse timeline = auditEventService.getIncidentTimeline(incidentId);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Retrieves audit events specifically associated with a recovery execution run.
     */
    @GetMapping("/recovery-executions/{executionId}/audit")
    public ResponseEntity<AuditTimelineResponse> getExecutionAuditTimeline(@PathVariable Long executionId) {
        AuditTimelineResponse timeline = auditEventService.getExecutionTimeline(executionId);
        return ResponseEntity.ok(timeline);
    }

    /**
     * Cryptographically verifies the SHA-256 tamper-evident hash chain for an incident's audit trail.
     */
    @GetMapping("/incidents/{incidentId}/audit/verify")
    public ResponseEntity<AuditVerificationResponse> verifyIncidentAuditChain(@PathVariable Long incidentId) {
        AuditVerificationResponse verification = auditEventService.verifyIncidentAuditChain(incidentId);
        return ResponseEntity.ok(verification);
    }
}
