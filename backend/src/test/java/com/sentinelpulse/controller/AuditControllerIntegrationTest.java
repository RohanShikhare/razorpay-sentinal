package com.sentinelpulse.controller;

import com.sentinelpulse.audit.AuditEventCategory;
import com.sentinelpulse.audit.AuditEventType;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.RecoveryExecution;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.StoppingReason;
import com.sentinelpulse.repository.AuditEventRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.RecoveryExecutionRepository;
import com.sentinelpulse.service.AuditEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private RecoveryExecutionRepository recoveryExecutionRepository;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        recoveryExecutionRepository.deleteAll();
        incidentRepository.deleteAll();
    }

    private Incident createIncident() {
        Instant now = Instant.now();
        Incident incident = new Incident(
                IncidentType.PAYMENT_DEGRADATION,
                IncidentSeverity.HIGH,
                now,
                now.minusSeconds(3600),
                100,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(60_000),
                BigDecimal.valueOf(50_000),
                88.0,
                IncidentStatus.DETECTED,
                3.5,
                78.5,
                75.0,
                "HDFC",
                PaymentMethod.UPI,
                null,
                60
        );
        return incidentRepository.save(incident);
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{incidentId}/audit returns chronological events and decision chain")
    void testGetIncidentAuditTimeline() throws Exception {
        Incident incident = createIncident();
        auditEventService.recordIncidentDetected(incident);
        auditEventService.recordAiDiagnosisRequested(incident.getId());

        mockMvc.perform(get("/api/v1/incidents/{incidentId}/audit", incident.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is(incident.getId().intValue())))
                .andExpect(jsonPath("$.totalEvents", is(2)))
                .andExpect(jsonPath("$.chainValid", is(true)))
                .andExpect(jsonPath("$.decisionChain.detected", is(true)))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].eventType", is("INCIDENT_DETECTED")))
                .andExpect(jsonPath("$.events[1].eventType", is("AI_DIAGNOSIS_REQUESTED")));
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{incidentId}/audit/verify returns cryptographic validity")
    void testVerifyIncidentAuditChain() throws Exception {
        Incident incident = createIncident();
        auditEventService.recordIncidentDetected(incident);

        mockMvc.perform(get("/api/v1/incidents/{incidentId}/audit/verify", incident.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is(incident.getId().intValue())))
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.eventCount", is(1)));
    }

    @Test
    @DisplayName("GET /api/v1/recovery-executions/{executionId}/audit returns execution-scoped events")
    void testGetExecutionAuditTimeline() throws Exception {
        Incident incident = createIncident();
        RecoveryExecution execution = new RecoveryExecution(
                incident.getId(), 1L, 1L, Instant.now(), Instant.now(),
                ExecutionStatus.COMPLETED, com.sentinelpulse.ai.RecoveryStrategyType.DELAYED_RETRY,
                10L, 10L, 10L, 0L, 0L,
                BigDecimal.valueOf(10_000), BigDecimal.valueOf(10_000),
                BigDecimal.ZERO, BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(10_000), 100.0, 100.0, 100.0,
                StoppingReason.COMPLETED_ALL_ELIGIBLE, "OK",
                true, 2, 30, 30, "SIM_V1", "{}"
        );
        execution = recoveryExecutionRepository.save(execution);

        auditEventService.recordRecoveryExecutionStarted(execution);
        auditEventService.recordRecoveryExecutionCompleted(execution);

        mockMvc.perform(get("/api/v1/recovery-executions/{executionId}/audit", execution.getId())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionId", is(execution.getId().intValue())))
                .andExpect(jsonPath("$.totalEvents", is(2)))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].eventType", is("RECOVERY_EXECUTION_STARTED")))
                .andExpect(jsonPath("$.events[1].eventType", is("RECOVERY_COMPLETED")));
    }

    @Test
    @DisplayName("GET audit endpoints are strictly read-only and idempotent (never mutate event count)")
    void testAuditEndpointsAreReadOnly() throws Exception {
        Incident incident = createIncident();
        auditEventService.recordIncidentDetected(incident);

        long initialCount = auditEventRepository.count();

        // Call GET endpoints multiple times
        mockMvc.perform(get("/api/v1/incidents/{incidentId}/audit", incident.getId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/incidents/{incidentId}/audit/verify", incident.getId()))
                .andExpect(status().isOk());

        long countAfterCalls = auditEventRepository.count();
        assertThat(countAfterCalls).isEqualTo(initialCount);
    }
}
