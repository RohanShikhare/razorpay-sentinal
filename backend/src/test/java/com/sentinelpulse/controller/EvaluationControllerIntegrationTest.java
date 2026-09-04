package com.sentinelpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.dto.EvaluationRunRequest;
import com.sentinelpulse.evaluation.EvaluationScenarioType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link EvaluationController} REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class EvaluationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("GET /api/v1/evaluation/scenarios - Lists all available evaluation scenario definitions")
    void testGetScenarios() throws Exception {
        mockMvc.perform(get("/api/v1/evaluation/scenarios")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].scenario", notNullValue()))
                .andExpect(jsonPath("$[0].name", notNullValue()))
                .andExpect(jsonPath("$[0].category", notNullValue()))
                .andExpect(jsonPath("$[0].expectedSafeStrategies", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/evaluation/run - Executes multi-scenario benchmark evaluation run")
    void testRunEvaluation() throws Exception {
        EvaluationRunRequest request = new EvaluationRunRequest(
                4, 42L, true, List.of(EvaluationScenarioType.BANK_X_UPI_DEGRADATION, EvaluationScenarioType.MERCHANT_SPECIFIC_DEGRADATION)
        );

        mockMvc.perform(post("/api/v1/evaluation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId", notNullValue()))
                .andExpect(jsonPath("$.evaluatorVersion", equalTo("EVAL_V1")))
                .andExpect(jsonPath("$.scenarioCount", equalTo(4)))
                .andExpect(jsonPath("$.status", equalTo("COMPLETED")))
                .andExpect(jsonPath("$.financial", notNullValue()))
                .andExpect(jsonPath("$.detection", notNullValue()))
                .andExpect(jsonPath("$.diagnosis", notNullValue()))
                .andExpect(jsonPath("$.strategy", notNullValue()))
                .andExpect(jsonPath("$.policy", notNullValue()))
                .andExpect(jsonPath("$.recovery", notNullValue()))
                .andExpect(jsonPath("$.safety", notNullValue()))
                .andExpect(jsonPath("$.incidents", hasSize(4)));
    }

    @Test
    @DisplayName("GET /api/v1/evaluation/latest - Retrieves the latest evaluation run")
    void testGetLatestEvaluationRun() throws Exception {
        // Run first
        EvaluationRunRequest request = new EvaluationRunRequest(2, 42L, true, null);
        mockMvc.perform(post("/api/v1/evaluation/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Fetch latest
        mockMvc.perform(get("/api/v1/evaluation/latest")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId", notNullValue()))
                .andExpect(jsonPath("$.evaluatorVersion", equalTo("EVAL_V1")))
                .andExpect(jsonPath("$.scenarioCount", equalTo(2)))
                .andExpect(jsonPath("$.financial.totalRevenueAtRisk", notNullValue()));
    }
}
