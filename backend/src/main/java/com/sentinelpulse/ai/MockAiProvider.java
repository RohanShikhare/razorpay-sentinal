package com.sentinelpulse.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sentinelpulse.dto.AiEvidenceDto;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import org.springframework.stereotype.Component;

/**
 * Deterministic Mock AI Provider for local development and automated testing.
 * Requires NO API key and guarantees reproducible diagnostic outcomes for known scenarios.
 */
@Component("mockAiProvider")
public class MockAiProvider implements AiProvider {

    private final ObjectMapper objectMapper;

    public MockAiProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getProviderName() {
        return "MOCK";
    }

    @Override
    public String getModelName() {
        return "mock-sentinel-diagnostician-v1";
    }

    @Override
    public String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction) {
        ObjectNode root = objectMapper.createObjectNode();

        if (evidence == null) {
            root.put("incidentType", "SYSTEM_DEGRADATION");
            root.put("rootCause", "Insufficient evidence supplied to perform diagnosis.");
            root.put("reasoning", "No evidence payload was provided to the analyzer.");
            root.put("confidence", 0.1);
            root.putArray("evidence").add("Evidence payload is empty");
            root.put("affectedSegment", "UNKNOWN");
            root.put("recommendedAction", ControlledAction.HUMAN_REVIEW.name());
            root.put("estimatedRecoveryPercentage", 0.0);
            root.put("riskLevel", RiskLevel.HIGH.name());
            root.put("requiresHumanApproval", true);
            return root.toString();
        }

        // Scenario 1: Bank X UPI Degradation
        if ("Bank X".equals(evidence.targetBank()) || (evidence.concentrationSummary() != null && "Bank X".equals(evidence.concentrationSummary().dominantBank()) && PaymentMethod.UPI.name().equals(evidence.concentrationSummary().dominantPaymentMethod()))) {
            root.put("incidentType", "PAYMENT_DEGRADATION");
            root.put("rootCause", "Bank X UPI Gateway upstream timeout and processing degradation");
            root.put("reasoning", String.format(
                    "Failure rate for Bank X UPI escalated sharply from %.2f%% to %.2f%% (+%.2f points increase). Failure error codes are overwhelmingly dominated by BANK_TIMEOUT, whereas other payment methods at Bank X and other banks remain nominal.",
                    evidence.baselineFailureRate(), evidence.currentFailureRate(), evidence.failureRateIncrease()
            ));
            root.put("confidence", 0.94);
            ArrayNode evList = root.putArray("evidence");
            evList.add(String.format("Bank X UPI failure rate increased from %.2f%% to %.2f%%", evidence.baselineFailureRate(), evidence.currentFailureRate()));
            evList.add("Concentration of failures in BANK_TIMEOUT error code");
            evList.add("Non-UPI traffic on Bank X exhibits baseline failure rates");
            root.put("affectedSegment", "Bank X + UPI");
            root.put("recommendedAction", ControlledAction.DELAYED_RETRY.name());
            root.put("estimatedRecoveryPercentage", 70.0);
            root.put("riskLevel", RiskLevel.HIGH.name());
            root.put("requiresHumanApproval", false);
            return root.toString();
        }

        // Scenario 2: Merchant-Specific Degradation
        if (evidence.incidentType() == IncidentType.MERCHANT_DEGRADATION || evidence.targetMerchantId() != null) {
            String merchant = evidence.targetMerchantId() != null ? evidence.targetMerchantId() : "merchant_hot";
            root.put("incidentType", "MERCHANT_DEGRADATION");
            root.put("rootCause", String.format("Integration or credential failure isolated strictly to merchant %s", merchant));
            root.put("reasoning", String.format(
                    "Failures are 100%% concentrated on merchant %s with elevated ISSUER_DECLINED and INSUFFICIENT_FUNDS across multiple banks. Underlying bank rails and network connectivity remain nominal.",
                    merchant
            ));
            root.put("confidence", 0.89);
            ArrayNode evList = root.putArray("evidence");
            evList.add(String.format("100%% failure concentration on merchant %s", merchant));
            evList.add(String.format("Merchant failure rate elevated to %.2f%%", evidence.currentFailureRate()));
            evList.add("Bank-level failure rates across other merchants remain nominal");
            root.put("affectedSegment", "Merchant " + merchant);
            root.put("recommendedAction", ControlledAction.HUMAN_REVIEW.name());
            root.put("estimatedRecoveryPercentage", 35.0);
            root.put("riskLevel", RiskLevel.MEDIUM.name());
            root.put("requiresHumanApproval", true);
            return root.toString();
        }

        // Scenario 3: System / Normal Random Failures
        root.put("incidentType", evidence.incidentType() != null ? evidence.incidentType().name() : "SYSTEM_DEGRADATION");
        root.put("rootCause", "Transient baseline variation across multi-bank payment infrastructure");
        root.put("reasoning", String.format(
                "Observed failure rate fluctuation of %.2f points is distributed without strong concentration in any individual bank or merchant rail. Consistent with background network noise.",
                evidence.failureRateIncrease()
        ));
        root.put("confidence", 0.65);
        ArrayNode evList = root.putArray("evidence");
        evList.add(String.format("Global failure rate increase is %.2f points", evidence.failureRateIncrease()));
        evList.add("No single bank or merchant exceeds 50% failure concentration");
        root.put("affectedSegment", "Global Payment System");
        root.put("recommendedAction", ControlledAction.MONITOR.name());
        root.put("estimatedRecoveryPercentage", 25.0);
        root.put("riskLevel", RiskLevel.LOW.name());
        root.put("requiresHumanApproval", false);
        return root.toString();
    }
}
