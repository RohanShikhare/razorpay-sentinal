package com.sentinelpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.dto.StoppingConditionsDto;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.entity.PolicyEvaluation;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PolicyEvaluationRepository;
import com.sentinelpulse.repository.RecoveryExecutionAttemptRepository;
import com.sentinelpulse.repository.RecoveryExecutionRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
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
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RecoveryExecutionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private RecoveryStrategyRepository recoveryStrategyRepository;

    @Autowired
    private PolicyEvaluationRepository policyEvaluationRepository;

    @Autowired
    private RecoveryExecutionRepository recoveryExecutionRepository;

    @Autowired
    private RecoveryExecutionAttemptRepository recoveryExecutionAttemptRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        recoveryExecutionAttemptRepository.deleteAll();
        recoveryExecutionRepository.deleteAll();
        policyEvaluationRepository.deleteAll();
        recoveryStrategyRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private Incident createTestIncident(Instant startTime) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                IncidentType.PAYMENT_DEGRADATION,
                IncidentSeverity.HIGH,
                now,
                startTime != null ? startTime : now.minusSeconds(3600),
                100,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(70_000),
                BigDecimal.valueOf(60_000),
                92.0,
                IncidentStatus.DETECTED,
                4.0,
                35.0,
                31.0,
                "Bank X",
                PaymentMethod.UPI,
                null,
                60
        );
        return incidentRepository.save(incident);
    }

    private RecoveryStrategy createTestStrategy(Long incidentId) throws Exception {
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = new RecoveryStrategy(
                incidentId,
                null,
                Instant.now(),
                StrategyStatus.PROPOSED,
                RecoveryStrategyType.DELAYED_RETRY,
                1,
                "Integration test strategy rationale",
                0.88,
                RiskLevel.MEDIUM,
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

    private PolicyEvaluation createTestPolicy(Long incidentId, Long strategyId, PolicyDecision decision, boolean executionAllowed) {
        PolicyEvaluation policy = new PolicyEvaluation(
                incidentId,
                strategyId,
                Instant.now(),
                "POLICY_V1",
                decision,
                true,
                executionAllowed,
                false,
                RiskLevel.MEDIUM,
                100.0,
                "Policy approved summary",
                executionAllowed ? null : "Blocked by policy",
                "Next step guidance",
                "[]",
                "[]",
                "[]",
                "[]"
        );
        return policyEvaluationRepository.save(policy);
    }

    private void createTestPayments(Instant startTime, int count) {
        List<Payment> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Payment p = new Payment(
                    "merchant_1",
                    "cust_" + i,
                    BigDecimal.valueOf(1500L * i),
                    "INR",
                    startTime.plusSeconds(100 * i),
                    PaymentMethod.UPI,
                    "Bank X",
                    PaymentStatus.FAILED,
                    ErrorCode.BANK_TIMEOUT
            );
            list.add(p);
        }
        paymentRepository.saveAll(list);
    }

    @Test
    @DisplayName("POST /api/v1/incidents/{id}/recovery-execute runs recovery simulation and returns result")
    void testExecuteRecoveryEndpoint() throws Exception {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(startTime);
        RecoveryStrategy strategy = createTestStrategy(incident.getId());
        createTestPolicy(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true);
        createTestPayments(startTime, 5);

        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/recovery-execute")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.strategyId").value(strategy.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.executionAllowed").value(true))
                .andExpect(jsonPath("$.eligibleTransactions").value(5))
                .andExpect(jsonPath("$.simulationDisclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id}/recovery-execution returns latest execution or 404")
    void testGetLatestExecutionEndpoint() throws Exception {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(startTime);
        RecoveryStrategy strategy = createTestStrategy(incident.getId());
        createTestPolicy(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true);
        createTestPayments(startTime, 5);

        // Before execution: 404
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/recovery-execution"))
                .andExpect(status().isNotFound());

        // Execute
        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/recovery-execute"))
                .andExpect(status().isOk());

        // After execution: 200
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/recovery-execution"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }
}
