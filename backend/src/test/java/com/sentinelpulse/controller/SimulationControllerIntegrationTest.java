package com.sentinelpulse.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.SimulationChangeRepository;
import com.sentinelpulse.repository.SimulationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private SimulationRunRepository simulationRunRepository;

    @Autowired
    private SimulationChangeRepository simulationChangeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDatabase() {
        simulationChangeRepository.deleteAll();
        simulationRunRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private void generatePayments(int count, long seed) throws Exception {
        mockMvc.perform(post("/api/v1/payments/generate")
                        .contentType("application/json")
                        .content("{\"count\":" + count + ",\"seed\":" + seed + "}"))
                .andExpect(status().isOk());
    }

    private JsonNode runScenario(String scenario) throws Exception {
        String body = mockMvc.perform(post("/api/v1/simulations/run")
                        .contentType("application/json")
                        .content("{\"scenario\":\"" + scenario + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    @Test
    void bankXUpiScenarioConcentratesFailuresOnBankXUpiOnly() throws Exception {
        generatePayments(3_000, 101L);

        JsonNode result = runScenario("BANK_X_UPI_DEGRADATION");

        double failureRateAfter = result.get("failureRateAfter").asDouble();
        assertTrue(failureRateAfter >= 20.0 && failureRateAfter <= 45.0,
                "Bank X + UPI failure rate should land in a clearly elevated range, got " + failureRateAfter);
        assertEquals("Bank X", result.get("targetBank").asText());
        assertEquals("UPI", result.get("targetPaymentMethod").asText());

        List<Payment> all = paymentRepository.findAll();

        double bankXUpiFailureRate = failureRateOf(all, p ->
                "Bank X".equals(p.getBank()) && p.getPaymentMethod() == PaymentMethod.UPI);
        assertTrue(bankXUpiFailureRate >= 20.0,
                "Bank X + UPI should show a clearly elevated failure rate, got " + bankXUpiFailureRate);

        // Unrelated bank/method combinations should stay close to the healthy baseline.
        double bankAUpiFailureRate = failureRateOf(all, p ->
                "Bank A".equals(p.getBank()) && p.getPaymentMethod() == PaymentMethod.UPI);
        double bankXCardFailureRate = failureRateOf(all, p ->
                "Bank X".equals(p.getBank()) && p.getPaymentMethod() == PaymentMethod.CARD);

        assertTrue(bankAUpiFailureRate < 15.0, "Bank A + UPI should be largely unaffected, got " + bankAUpiFailureRate);
        assertTrue(bankXCardFailureRate < 15.0, "Bank X + CARD should be largely unaffected, got " + bankXCardFailureRate);

        // Newly-failed Bank X + UPI payments should mostly carry BANK_TIMEOUT.
        long bankXUpiFailed = all.stream()
                .filter(p -> "Bank X".equals(p.getBank()) && p.getPaymentMethod() == PaymentMethod.UPI)
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .count();
        long bankXUpiFailedWithTimeout = all.stream()
                .filter(p -> "Bank X".equals(p.getBank()) && p.getPaymentMethod() == PaymentMethod.UPI)
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .filter(p -> p.getErrorCode() == com.sentinelpulse.entity.ErrorCode.BANK_TIMEOUT)
                .count();
        assertTrue(bankXUpiFailedWithTimeout > bankXUpiFailed / 2,
                "Most Bank X + UPI failures should carry BANK_TIMEOUT");
    }

    @Test
    void merchantScenarioDisproportionatelyAffectsOneMerchant() throws Exception {
        generatePayments(3_000, 202L);

        JsonNode result = runScenario("MERCHANT_SPECIFIC_DEGRADATION");
        String targetMerchant = result.get("targetMerchantId").asText();
        assertTrue(targetMerchant != null && !targetMerchant.isBlank());

        List<Payment> all = paymentRepository.findAll();
        Map<String, List<Payment>> byMerchant = all.stream().collect(Collectors.groupingBy(Payment::getMerchantId));

        double targetFailureRate = failureRateOf(byMerchant.get(targetMerchant), p -> true);
        assertTrue(targetFailureRate >= 20.0,
                "target merchant should show a clearly elevated failure rate, got " + targetFailureRate);

        double averageOtherFailureRate = byMerchant.entrySet().stream()
                .filter(e -> !e.getKey().equals(targetMerchant))
                .mapToDouble(e -> failureRateOf(e.getValue(), p -> true))
                .average()
                .orElse(0.0);
        assertTrue(averageOtherFailureRate < 15.0,
                "other merchants should remain comparatively normal, got average " + averageOtherFailureRate);
    }

    @Test
    void normalFailuresScenarioDoesNotConcentrateOnOneSegment() throws Exception {
        generatePayments(3_000, 303L);

        JsonNode result = runScenario("NORMAL_RANDOM_FAILURES");
        double after = result.get("failureRateAfter").asDouble();

        // A modest bump only, not a dramatic spike.
        assertTrue(after <= 12.0, "normal noise should not spike dramatically, got " + after);

        List<Payment> all = paymentRepository.findAll();
        Map<String, Double> byBankFailureRate = all.stream()
                .collect(Collectors.groupingBy(Payment::getBank))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> failureRateOf(e.getValue(), p -> true)));

        // No single bank should be dramatically elevated relative to the others.
        for (double rate : byBankFailureRate.values()) {
            assertTrue(rate <= 20.0, "no bank should show a dramatic isolated spike, got " + rate);
        }
    }

    @Test
    void resetRestoresHealthyState() throws Exception {
        generatePayments(1_000, 404L);

        long failedBefore = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED).count();

        runScenario("BANK_X_UPI_DEGRADATION");

        long failedDuring = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        assertTrue(failedDuring > failedBefore, "simulation should increase failures");

        mockMvc.perform(post("/api/v1/simulations/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reset").value(true));

        long failedAfterReset = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        assertEquals(failedBefore, failedAfterReset, "reset should restore the pre-simulation failure count");

        mockMvc.perform(get("/api/v1/simulations/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void runningANewSimulationWhileOneIsActiveDoesNotCompoundFailures() throws Exception {
        generatePayments(2_000, 505L);

        JsonNode first = runScenario("BANK_X_UPI_DEGRADATION");
        JsonNode second = runScenario("BANK_X_UPI_DEGRADATION");

        assertTrue(second.get("previousSimulationReset").asBoolean(),
                "starting a new simulation while one is active should reset the previous one first");

        double firstAfter = first.get("failureRateAfter").asDouble();
        double secondAfter = second.get("failureRateAfter").asDouble();

        // Repeating the same scenario should land in the same target range,
        // not compound into a higher and higher failure rate.
        assertTrue(Math.abs(firstAfter - secondAfter) < 5.0,
                "repeated runs should not compound: " + firstAfter + " vs " + secondAfter);

        mockMvc.perform(get("/api/v1/simulations/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.scenario").value("BANK_X_UPI_DEGRADATION"));
    }

    private static double failureRateOf(List<Payment> payments, java.util.function.Predicate<Payment> predicate) {
        List<Payment> matching = payments.stream().filter(predicate).toList();
        if (matching.isEmpty()) {
            return 0.0;
        }
        long failed = matching.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        return (failed * 100.0) / matching.size();
    }
}
