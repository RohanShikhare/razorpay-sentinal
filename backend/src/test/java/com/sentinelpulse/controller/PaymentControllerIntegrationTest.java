package com.sentinelpulse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end tests against a real (in-memory H2) database, exercising the
 * actual HTTP layer, JPA persistence, filtering, pagination, and the
 * summary aggregation together.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PaymentControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        paymentRepository.deleteAll();
    }

    @Test
    void generatingOneThousandPaymentsPersistsThemAll() throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new GenerateBody(1_000, 7L))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedCount").value(1_000))
                .andExpect(jsonPath("$.generatedCount").value(1_000));

        assertEquals(1_000, paymentRepository.count(), "every generated payment should be persisted");
    }

    @Test
    void rejectsOutOfRangeGenerationCounts() throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new GenerateBody(0, null))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/payments/generate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new GenerateBody(100_000_000, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listEndpointReturnsPaginatedResults() throws Exception {
        generate(250, 11L);

        mockMvc.perform(get("/api/v1/payments").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(250))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void statusFilterOnlyReturnsMatchingPayments() throws Exception {
        generate(1_000, 21L);

        mockMvc.perform(get("/api/v1/payments")
                        .param("status", "FAILED")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("FAILED"))));
    }

    @Test
    void bankAndPaymentMethodFiltersCombine() throws Exception {
        generate(1_000, 33L);

        mockMvc.perform(get("/api/v1/payments")
                        .param("bank", "Bank X")
                        .param("paymentMethod", "UPI")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].bank").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("Bank X"))))
                .andExpect(jsonPath("$.content[*].paymentMethod").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("UPI"))));
    }

    @Test
    void summaryCountsAreMathematicallyConsistent() throws Exception {
        generate(2_000, 55L);

        String body = mockMvc.perform(get("/api/v1/payments/summary"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var summary = objectMapper.readTree(body);
        long total = summary.get("totalTransactions").asLong();
        long success = summary.get("successfulTransactions").asLong();
        long failed = summary.get("failedTransactions").asLong();
        long pending = summary.get("pendingTransactions").asLong();
        double successRate = summary.get("successRate").asDouble();

        assertEquals(2_000, total);
        assertEquals(total, success + failed + pending,
                "success + failed + pending must equal total transactions");

        double expectedSuccessRate = Math.round((success / (double) total) * 100 * 100) / 100.0;
        assertEquals(expectedSuccessRate, successRate, 0.01,
                "successRate must equal successful/total * 100");
    }

    @Test
    void summaryBreakdownsIncludeFailureRatePerSegment() throws Exception {
        generate(2_000, 66L);

        String body = mockMvc.perform(get("/api/v1/payments/summary"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var summary = objectMapper.readTree(body);
        long totalFailed = summary.get("failedTransactions").asLong();

        long sumOfSegmentFailedByBank = 0;
        for (var segment : summary.get("byBank")) {
            long count = segment.get("count").asLong();
            long failedCount = segment.get("failedCount").asLong();
            double failureRate = segment.get("failureRate").asDouble();
            sumOfSegmentFailedByBank += failedCount;

            double expectedRate = count == 0 ? 0.0 : Math.round((failedCount / (double) count) * 100 * 100) / 100.0;
            assertEquals(expectedRate, failureRate, 0.02,
                    "per-bank failureRate should equal failedCount/count * 100");
        }
        assertEquals(totalFailed, sumOfSegmentFailedByBank,
                "failed counts across all banks should sum to the total failed count");

        long sumOfSegmentFailedByMethod = 0;
        for (var segment : summary.get("byPaymentMethod")) {
            sumOfSegmentFailedByMethod += segment.get("failedCount").asLong();
        }
        assertEquals(totalFailed, sumOfSegmentFailedByMethod,
                "failed counts across all payment methods should sum to the total failed count");

        long sumOfErrorCodeCounts = 0;
        double sumOfErrorCodePercentages = 0.0;
        for (var segment : summary.get("byErrorCode")) {
            sumOfErrorCodeCounts += segment.get("count").asLong();
            sumOfErrorCodePercentages += segment.get("percentageOfFailures").asDouble();
        }
        assertEquals(totalFailed, sumOfErrorCodeCounts,
                "error code counts should sum to the total failed count (every failure has a real error code)");
        assertEquals(100.0, sumOfErrorCodePercentages, 0.5,
                "error code percentages should sum to ~100%");
    }

    private void generate(int count, long seed) throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(new GenerateBody(count, seed))))
                .andExpect(status().isOk());
    }

    private record GenerateBody(Integer count, Long seed) {
    }
}
