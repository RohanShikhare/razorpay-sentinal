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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FinancialImpactControllerIntegrationTest {

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
    void bankXUpiIncidentHasConsistentFinancialImpact() throws Exception {
        generatePayments(3_000, 2001L);
        runScenario("BANK_X_UPI_DEGRADATION");
        JsonNode detection = detect();
        assertTrue(detection.get("incidentDetected").asBoolean());
        long incidentId = detection.get("incidentId").asLong();

        String impactBody = mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/financial-impact"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode impact = objectMapper.readTree(impactBody);

        double revenueAtRisk = impact.get("revenueAtRisk").asDouble();
        double recoverable = impact.get("estimatedRecoverableRevenue").asDouble();
        double nonRecoverable = impact.get("nonRecoverableRevenue").asDouble();

        assertTrue(revenueAtRisk >= 0, "revenueAtRisk should be non-negative");
        assertTrue(recoverable <= revenueAtRisk + 0.01, "recoverable must not exceed revenueAtRisk");
        assertTrue(Math.abs((recoverable + nonRecoverable) - revenueAtRisk) < 0.02,
                "recoverable + nonRecoverable should equal revenueAtRisk, got " + recoverable + " + " + nonRecoverable
                        + " vs " + revenueAtRisk);
        assertTrue(impact.get("affectedTransactions").asLong() > 0);
    }

    @Test
    void bankXUpiBreakdownByErrorCodeIsDominatedByBankTimeout() throws Exception {
        generatePayments(3_000, 2002L);
        runScenario("BANK_X_UPI_DEGRADATION");
        JsonNode detection = detect();
        long incidentId = detection.get("incidentId").asLong();

        String breakdownBody = mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/financial-impact/breakdown"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode breakdown = objectMapper.readTree(breakdownBody);

        JsonNode byErrorCode = breakdown.get("byErrorCode");
        assertTrue(byErrorCode.isArray() && byErrorCode.size() > 0);
        String topErrorCode = byErrorCode.get(0).get("segment").asText();
        assertTrue(topErrorCode.equals("BANK_TIMEOUT"),
                "expected BANK_TIMEOUT to dominate Bank X UPI failures, got " + topErrorCode);
    }

    @Test
    void merchantScenarioDoesNotBlameBankTimeoutInBreakdown() throws Exception {
        generatePayments(3_000, 2003L);
        runScenario("MERCHANT_SPECIFIC_DEGRADATION");
        JsonNode detection = detect();
        assertTrue(detection.get("incidentDetected").asBoolean());
        long incidentId = detection.get("incidentId").asLong();

        String breakdownBody = mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/financial-impact/breakdown"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode breakdown = objectMapper.readTree(breakdownBody);

        JsonNode byErrorCode = breakdown.get("byErrorCode");
        assertTrue(byErrorCode.isArray() && byErrorCode.size() > 0);
        String topErrorCode = byErrorCode.get(0).get("segment").asText();
        assertTrue(!topErrorCode.equals("BANK_TIMEOUT"),
                "a merchant-driven incident's failures should not be dominated by a bank-flavored error code");
    }

    @Test
    void evidenceEndpointReturnsStructuredMapsForFutureAiUse() throws Exception {
        generatePayments(3_000, 2004L);
        runScenario("BANK_X_UPI_DEGRADATION");
        JsonNode detection = detect();
        long incidentId = detection.get("incidentId").asLong();

        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/evidence"))
                .andExpect(status().isOk());
    }
}
