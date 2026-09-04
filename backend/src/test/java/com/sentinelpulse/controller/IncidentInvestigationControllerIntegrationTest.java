package com.sentinelpulse.controller;

import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.repository.IncidentInvestigationRepository;
import com.sentinelpulse.repository.IncidentRepository;
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
class IncidentInvestigationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentInvestigationRepository incidentInvestigationRepository;

    @Autowired
    private PaymentGeneratorService paymentGeneratorService;

    @BeforeEach
    void setUp() {
        incidentInvestigationRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentGeneratorService.generateAndPersist(500, 201L);
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
    @DisplayName("POST /api/v1/incidents/{id}/investigate triggers deep investigation")
    void testInvestigateEndpoint() throws Exception {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/investigate")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.strongestRootCause").isNotEmpty())
                .andExpect(jsonPath("$.overallConfidence").isNumber())
                .andExpect(jsonPath("$.hypotheses").isArray())
                .andExpect(jsonPath("$.hypotheses[0].hypothesis").isNotEmpty())
                .andExpect(jsonPath("$.hypotheses[0].verdict").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/v1/incidents/{id}/investigation returns latest investigation")
    void testGetLatestInvestigationEndpoint() throws Exception {
        Incident incident = createTestIncident(IncidentType.MERCHANT_DEGRADATION, null, null, "merchant_inv_test");

        // Before investigation, should return 404
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/investigation"))
                .andExpect(status().isNotFound());

        // Run investigation
        mockMvc.perform(post("/api/v1/incidents/" + incident.getId() + "/investigate"))
                .andExpect(status().isOk());

        // Now GET should return 200 with the saved investigation
        mockMvc.perform(get("/api/v1/incidents/" + incident.getId() + "/investigation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value(incident.getId()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.affectedSegment").value("Merchant merchant_inv_test"));
    }
}
