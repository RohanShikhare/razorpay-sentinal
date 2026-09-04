package com.sentinelpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.dto.StoppingConditionsDto;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PolicyEvaluationRepository;
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
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyEvaluationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private RecoveryStrategyRepository recoveryStrategyRepository;

    @Autowired
    private PolicyEvaluationRepository policyEvaluationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGeneratorService paymentGeneratorService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        policyEvaluationRepository.deleteAll();
        recoveryStrategyRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
        paymentGeneratorService.generateAndPersist(300, 401L);
    }

    private Incident createTestIncident(IncidentType type, String bank, PaymentMethod method, String merchant) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                type,
                IncidentSeverity.HIGH,
                now,
                now.minusSeconds(3600),
                100,
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

    private RecoveryStrategy createTestStrategy(Long incidentId, RecoveryStrategyType type, RiskLevel risk, double confidence) throws Exception {
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = new RecoveryStrategy(
                incidentId,
                null,
                Instant.now(),
                StrategyStatus.PROPOSED,
                type,
                1,
                "Integration test strategy rationale",
                confidence,
                risk,
                "Bank X + UPI",
                BigDecimal.valueOf(60_000),
                BigDecimal.valueOf(50_000),
                70.0,
                BigDecimal.valueOf(40_000),
                50L,
                objectMapper.writeValueAsString(conditions),
                false,
                "[]",
                StrategySource.DETERMINISTIC_RULES,
                "TEST_MODEL",
                null
        );
        return recoveryStrategyRepository.save(strategy);
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/policy-evaluate evaluates strategy and persists policy decision")
    void testPolicyEvaluateEndpoint() throws Exception {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);
        createTestStrategy(incident.getId(), RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88);

        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/policy-evaluate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.evaluatorVersion").value("POLICY_V1"))
                .andExpect(jsonPath("$.decision").isNotEmpty())
                .andExpect(jsonPath("$.overallAllowed").value(true))
                .andExpect(jsonPath("$.policyChecks").isArray())
                .andExpect(jsonPath("$.advisoryDisclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id}/policy-evaluation returns latest evaluation or 404 if none exist")
    void testGetLatestPolicyEvaluationEndpoint() throws Exception {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);
        createTestStrategy(incident.getId(), RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88);

        // Before evaluation: should return 404
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/policy-evaluation"))
                .andExpect(status().isNotFound());

        // Trigger evaluation
        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/policy-evaluate"))
                .andExpect(status().isOk());

        // After evaluation: should return 200 with persisted policy evaluation
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/policy-evaluation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.evaluatorVersion").value("POLICY_V1"))
                .andExpect(jsonPath("$.decision").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/policy-evaluate returns 404 when no recovery strategy exists")
    void testEvaluateWithoutStrategy_returns404() throws Exception {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        // No recovery strategy created for this incident
        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/policy-evaluate"))
                .andExpect(status().isNotFound());
    }
}
