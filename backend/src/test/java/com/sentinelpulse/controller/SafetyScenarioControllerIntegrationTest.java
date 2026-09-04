package com.sentinelpulse.controller;

import com.sentinelpulse.safety.SafetyScenarioType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link SafetyScenarioController} REST API.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SafetyScenarioControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/safety/scenarios - Lists all 12 scenario descriptors")
    void testGetScenarios() throws Exception {
        mockMvc.perform(get("/api/v1/safety/scenarios")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].type", notNullValue()))
                .andExpect(jsonPath("$[0].name", notNullValue()))
                .andExpect(jsonPath("$[0].category", notNullValue()))
                .andExpect(jsonPath("$[0].safetyProperty", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/safety/scenarios/POLICY_REJECTION/run - Runs single scenario")
    void testRunSingleScenario() throws Exception {
        mockMvc.perform(post("/api/v1/safety/scenarios/POLICY_REJECTION/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario", equalTo(SafetyScenarioType.POLICY_REJECTION.name())))
                .andExpect(jsonPath("$.passed", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo("PASSED")))
                .andExpect(jsonPath("$.policyDecision", equalTo("REJECTED")))
                .andExpect(jsonPath("$.executionAllowed", equalTo(false)))
                .andExpect(jsonPath("$.recoveryStatus", equalTo("BLOCKED")));
    }

    @Test
    @DisplayName("POST /api/v1/safety/scenarios/CIRCUIT_BREAKER/run - Runs circuit breaker scenario")
    void testRunCircuitBreakerScenario() throws Exception {
        mockMvc.perform(post("/api/v1/safety/scenarios/CIRCUIT_BREAKER/run")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenario", equalTo(SafetyScenarioType.CIRCUIT_BREAKER.name())))
                .andExpect(jsonPath("$.passed", equalTo(true)))
                .andExpect(jsonPath("$.status", equalTo("PASSED")))
                .andExpect(jsonPath("$.recoveryStatus", equalTo("STOPPED")))
                .andExpect(jsonPath("$.stoppingReason", equalTo("CIRCUIT_BREAKER_FAILURE_RATE")));
    }

    @Test
    @DisplayName("POST /api/v1/safety/scenarios/run-all - Runs complete scenario lab test suite")
    void testRunAllScenarios() throws Exception {
        mockMvc.perform(post("/api/v1/safety/scenarios/run-all")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].passed", equalTo(true)))
                .andExpect(jsonPath("$[1].passed", equalTo(true)));
    }
}
