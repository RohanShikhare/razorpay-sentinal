package com.sentinelpulse.controller;

import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
import com.sentinelpulse.service.PaymentGeneratorService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecoveryStrategyControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private RecoveryStrategyRepository recoveryStrategyRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGeneratorService paymentGeneratorService;

    @BeforeEach
    void setUp() {
        recoveryStrategyRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
        paymentGeneratorService.generateAndPersist(500, 301L);
    }

    private Incident createTestIncident(IncidentType type, String bank, PaymentMethod method, String merchant) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                type,
                IncidentSeverity.CRITICAL,
                now,
                now.minusSeconds(3600),
                120,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(70_000),
                BigDecimal.valueOf(60_000),
                92.0,
                IncidentStatus.DETECTED,
                4.0,
                35.0,
                31.0,
                bank,
                method,
                merchant,
                60
        );
        return incidentRepository.save(incident);
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/recovery-strategy generates bounded recovery strategy")
    void testGenerateRecoveryStrategyEndpoint() throws Exception {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/recovery-strategy")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.selectedStrategy").value("DELAYED_RETRY"))
                .andExpect(jsonPath("$.confidence").isNumber())
                .andExpect(jsonPath("$.expectedRecoveryPercentage").value(70.0))
                .andExpect(jsonPath("$.authoritativeRevenueAtRisk").isNumber())
                .andExpect(jsonPath("$.authoritativeRecoverableAmount").isNumber())
                .andExpect(jsonPath("$.estimatedRecoveryAmount").isNumber())
                .andExpect(jsonPath("$.stoppingConditions.maxRetryAttempts").value(2))
                .andExpect(jsonPath("$.alternatives").isArray())
                .andExpect(jsonPath("$.advisoryDisclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id}/recovery-strategy returns latest strategy or 404 when none exist")
    void testGetLatestRecoveryStrategyEndpoint() throws Exception {
        Incident incident = createTestIncident(IncidentType.MERCHANT_DEGRADATION, null, null, "merchant_strat_test");

        // Before generation: should return 404
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/recovery-strategy"))
                .andExpect(status().isNotFound());

        // Generate strategy
        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/recovery-strategy"))
                .andExpect(status().isOk());

        // After generation: should return 200 with persisted strategy
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/recovery-strategy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.selectedStrategy").value("HUMAN_REVIEW"))
                .andExpect(jsonPath("$.requiresHumanApproval").value(true));
    }
}
