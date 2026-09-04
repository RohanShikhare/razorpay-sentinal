package com.sentinelpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AiDiagnosticControllerIntegrationTest {

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
    private IncidentDiagnosisRepository incidentDiagnosisRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        incidentDiagnosisRepository.deleteAll();
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
    void diagnoseAndGetDiagnosisEndpointsWorkForBankXUpiIncident() throws Exception {
        generatePayments(3_000, 2001L);
        runScenario("BANK_X_UPI_DEGRADATION");
        JsonNode detectionResult = detect();

        assertTrue(detectionResult.get("incidentDetected").asBoolean());
        long incidentId = detectionResult.get("incidentId").asLong();

        // 1. Diagnosis endpoint returns 404 prior to running diagnose
        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/diagnosis"))
                .andExpect(status().isNotFound());

        // 2. Trigger AI diagnosis via POST
        String diagnoseBody = mockMvc.perform(post("/api/v1/incidents/" + incidentId + "/diagnose"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is((int) incidentId)))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.provider", is("MOCK")))
                .andExpect(jsonPath("$.rootCause", notNullValue()))
                .andExpect(jsonPath("$.confidence", notNullValue()))
                .andExpect(jsonPath("$.recommendedAction", notNullValue()))
                .andExpect(jsonPath("$.riskLevel", notNullValue()))
                .andExpect(jsonPath("$.requiresHumanApproval", notNullValue()))
                .andExpect(jsonPath("$.evidence").isArray())
                .andReturn().getResponse().getContentAsString();

        JsonNode diagnosisJson = objectMapper.readTree(diagnoseBody);
        String rootCause = diagnosisJson.get("rootCause").asText();
        assertTrue(rootCause.contains("Bank X UPI"));

        // 3. GET diagnosis now returns the latest diagnosis
        mockMvc.perform(get("/api/v1/incidents/" + incidentId + "/diagnosis"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId", is((int) incidentId)))
                .andExpect(jsonPath("$.rootCause", is(rootCause)));

        // 4. Incident detail now shows updated rootCause and INVESTIGATING status
        String incidentDetail = mockMvc.perform(get("/api/v1/incidents/" + incidentId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode detailJson = objectMapper.readTree(incidentDetail);
        assertEquals(rootCause, detailJson.get("incident").get("rootCause").asText());
        assertEquals("INVESTIGATING", detailJson.get("incident").get("status").asText());
    }

    @Test
    void nonExistentIncidentReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/incidents/999999/diagnose"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/incidents/999999/diagnosis"))
                .andExpect(status().isNotFound());
    }
}
