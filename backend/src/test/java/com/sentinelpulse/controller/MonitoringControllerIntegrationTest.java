package com.sentinelpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.SimulationChangeRepository;
import com.sentinelpulse.repository.SimulationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MonitoringControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SimulationRunRepository simulationRunRepository;

    @Autowired
    private SimulationChangeRepository simulationChangeRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        simulationChangeRepository.deleteAll();
        simulationRunRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private void generatePayments(int count, long seed) throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate")
                        .contentType("application/json")
                        .content("{\"count\":" + count + ",\"seed\":" + seed + "}"))
                .andExpect(status().isOk());
    }

    private void runScenario(String scenario) throws Exception {
        mockMvc.perform(post("/api/v1/simulations/run")
                        .contentType("application/json")
                        .content("{\"scenario\":\"" + scenario + "\"}"))
                .andExpect(status().isOk());
    }

    private JsonNode detect() throws Exception {
        String body = mockMvc.perform(post("/api/v1/monitoring/detect")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void healthyDatasetDoesNotProduceACriticalIncident() throws Exception {
        generatePayments(3_000, 1001L);

        JsonNode result = detect();

        if (result.get("incidentDetected").asBoolean()) {
            String severity = result.get("severity").asText();
            assertTrue(severity.equals("LOW") || severity.equals("MEDIUM"),
                    "a healthy dataset should not trigger HIGH/CRITICAL, got " + severity);
        }
        assertTrue(result.get("currentFailureRate").asDouble() < 20.0,
                "healthy current failure rate should stay low");
    }

    @Test
    void bankXUpiDegradationIsDetectedAsPaymentDegradation() throws Exception {
        generatePayments(3_000, 1002L);
        runScenario("BANK_X_UPI_DEGRADATION");

        JsonNode result = detect();

        assertTrue(result.get("incidentDetected").asBoolean(), "an incident should be detected");
        String severity = result.get("severity").asText();
        assertTrue(severity.equals("HIGH") || severity.equals("CRITICAL"),
                "Bank X UPI degradation should be HIGH or CRITICAL, got " + severity);
        assertEquals("PAYMENT_DEGRADATION", result.get("incidentType").asText());
        assertEquals("Bank X", result.get("targetBank").asText());
        assertEquals("UPI", result.get("targetPaymentMethod").asText());
        assertTrue(result.get("affectedTransactions").asLong() > 0);
        assertTrue(result.get("revenueAtRisk").asDouble() >= 0);
        long incidentId = result.get("incidentId").asLong();

        String detailBody = mockMvc.perform(get("/api/v1/incidents/" + incidentId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode detail = objectMapper.readTree(detailBody);
        assertTrue(detail.get("incident").get("rootCause").isNull(),
                "the deterministic detector must never assign a root cause");
        assertEquals("DETECTED", detail.get("incident").get("status").asText());
    }

    @Test
    void merchantScenarioIsDetectedAsMerchantDegradationNotBankBlame() throws Exception {
        generatePayments(3_000, 1003L);
        runScenario("MERCHANT_SPECIFIC_DEGRADATION");

        JsonNode result = detect();

        assertTrue(result.get("incidentDetected").asBoolean(), "the merchant incident should be detected");
        assertEquals("MERCHANT_DEGRADATION", result.get("incidentType").asText());
        assertTrue(result.get("targetMerchantId").asText() != null && !result.get("targetMerchantId").asText().isBlank());
        assertTrue(result.get("targetBank").isNull(), "a merchant-driven incident must not blame a bank");
    }

    @Test
    void normalRandomFailuresDoesNotProduceACriticalIncident() throws Exception {
        generatePayments(3_000, 1004L);
        runScenario("NORMAL_RANDOM_FAILURES");

        JsonNode result = detect();

        if (result.get("incidentDetected").asBoolean()) {
            String severity = result.get("severity").asText();
            assertTrue(!severity.equals("CRITICAL"),
                    "normal background noise should not be classified CRITICAL, got " + severity);
        }
    }

    @Test
    void repeatedDetectionDoesNotCreateDuplicateActiveIncidents() throws Exception {
        generatePayments(3_000, 1005L);
        runScenario("BANK_X_UPI_DEGRADATION");

        detect();
        detect();
        detect();

        long paymentDegradationIncidents = incidentRepository.findAll().stream()
                .filter(i -> i.getType().name().equals("PAYMENT_DEGRADATION"))
                .filter(i -> "Bank X".equals(i.getTargetBank()))
                .count();

        assertEquals(1, paymentDegradationIncidents,
                "repeated detection of the same condition should refresh, not duplicate, the incident");
    }

    @Test
    void incidentListEndpointReturnsPaginatedResults() throws Exception {
        generatePayments(3_000, 1006L);
        runScenario("BANK_X_UPI_DEGRADATION");
        detect();

        mockMvc.perform(get("/api/v1/incidents").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void monitoringSummaryReflectsActiveIncidents() throws Exception {
        generatePayments(3_000, 1007L);
        runScenario("BANK_X_UPI_DEGRADATION");
        detect();

        mockMvc.perform(get("/api/v1/monitoring/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeIncidents").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.paymentHealth").value(org.hamcrest.Matchers.not("NORMAL")));
    }
}
