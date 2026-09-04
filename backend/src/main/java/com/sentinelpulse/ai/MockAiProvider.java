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

    @Override
    public String generateInvestigationJson(AiEvidenceDto evidence, String diagnosisSummary, String systemInstruction) {
        ObjectNode root = objectMapper.createObjectNode();

        if (evidence == null) {
            root.put("incidentType", "SYSTEM_DEGRADATION");
            root.put("summary", "Investigation aborted: no deterministic evidence supplied.");
            root.put("strongestRootCause", "Insufficient evidence payload to determine root cause.");
            root.put("overallConfidence", 0.1);
            root.put("affectedSegment", "UNKNOWN");
            root.putArray("hypotheses");
            root.putArray("supportingEvidence").add("Evidence payload is null");
            root.putArray("contradictingEvidence");
            root.put("reasoning", "No telemetry was available for evaluation.");
            root.put("uncertainty", "Telemetry data missing entirely.");
            root.put("requiresHumanReview", true);
            return root.toString();
        }

        // Scenario 1: Bank X UPI Degradation
        if ("Bank X".equals(evidence.targetBank()) || (evidence.concentrationSummary() != null && "Bank X".equals(evidence.concentrationSummary().dominantBank()) && PaymentMethod.UPI.name().equals(evidence.concentrationSummary().dominantPaymentMethod()))) {
            root.put("incidentType", "PAYMENT_DEGRADATION");
            root.put("summary", "Root-cause investigation confirms an upstream gateway timeout degradation isolated specifically to Bank X UPI switch infrastructure.");
            root.put("strongestRootCause", "Bank X UPI Gateway upstream timeout and processing degradation");
            root.put("overallConfidence", 0.94);
            root.put("affectedSegment", "Bank X + UPI");

            ArrayNode hypotheses = root.putArray("hypotheses");

            // Hypothesis A
            ObjectNode hA = hypotheses.addObject();
            hA.put("hypothesis", "Bank X UPI Gateway / Switch Degradation");
            hA.put("confidence", 0.94);
            ArrayNode hASupp = hA.putArray("supportingEvidence");
            hASupp.add(String.format("Bank X UPI failure rate spiked from %.2f%% to %.2f%% (+%.2f pts)", evidence.baselineFailureRate(), evidence.currentFailureRate(), evidence.failureRateIncrease()));
            hASupp.add("87.5% concentration of failures under BANK_TIMEOUT error code");
            hASupp.add("Multiple unrelated merchants calling Bank X UPI experience identical degradation");
            ArrayNode hACont = hA.putArray("contradictingEvidence");
            hACont.add("Card and Netbanking transactions at Bank X show nominal baseline failure rates (~3.5%)");
            hA.put("affectedSegment", "Bank X + UPI");
            hA.put("verdict", "STRONGEST");

            // Hypothesis B
            ObjectNode hB = hypotheses.addObject();
            hB.put("hypothesis", "National UPI Switch (NPCI) Central Rail Outage");
            hB.put("confidence", 0.25);
            ArrayNode hBSupp = hB.putArray("supportingEvidence");
            hBSupp.add("Affected transactions utilize the UPI protocol");
            ArrayNode hBCont = hB.putArray("contradictingEvidence");
            hBCont.add("Other major banks (HDFC, ICICI, SBI, Axis) process UPI at normal baseline error rates (<4%)");
            hBCont.add("Failures are strictly contained to Bank X issuer handles");
            hB.put("affectedSegment", "All Banks UPI");
            hB.put("verdict", "REFUTED");

            // Hypothesis C
            ObjectNode hC = hypotheses.addObject();
            hC.put("hypothesis", "Merchant Integration Payload / Auth Misconfiguration");
            hC.put("confidence", 0.12);
            ArrayNode hCSupp = hC.putArray("supportingEvidence");
            ArrayNode hCCont = hC.putArray("contradictingEvidence");
            hCCont.add("Failures are distributed across multiple independent merchants");
            hCCont.add("Error code is BANK_TIMEOUT rather than BAD_REQUEST or AUTH_FAILED");
            hC.put("affectedSegment", "All Merchants");
            hC.put("verdict", "REFUTED");

            // Hypothesis D
            ObjectNode hD = hypotheses.addObject();
            hD.put("hypothesis", "Stochastic Baseline Noise / Traffic Volume Anomaly");
            hD.put("confidence", 0.04);
            ArrayNode hDSupp = hD.putArray("supportingEvidence");
            ArrayNode hDCont = hD.putArray("contradictingEvidence");
            hDCont.add(String.format("Failure rate increase (+%.2f pts) exceeds statistical 3-sigma anomaly threshold", evidence.failureRateIncrease()));
            hD.put("affectedSegment", "Global");
            hD.put("verdict", "REFUTED");

            ArrayNode supp = root.putArray("supportingEvidence");
            supp.add(String.format("Failure rate surge concentrated in Bank X UPI (+%.2f pts)", evidence.failureRateIncrease()));
            supp.add("Dominant failure mode is BANK_TIMEOUT (87.5% concentration)");
            supp.add("Other banks on UPI rail maintain normal baseline failure rates");

            ArrayNode cont = root.putArray("contradictingEvidence");
            cont.add("Bank X non-UPI payment methods (Cards, Netbanking) remain healthy");

            root.put("reasoning", String.format(
                    "Deterministic telemetry demonstrates high segment concentration: failure rate increased from %.2f%% to %.2f%% strictly on Bank X UPI. The dominant error mode is BANK_TIMEOUT. Comparing competing hypotheses, National UPI Switch failure is refuted because other banks on UPI are operating normally. Merchant-level misconfiguration is refuted because failures span multiple independent merchants.",
                    evidence.baselineFailureRate(), evidence.currentFailureRate()
            ));
            root.put("uncertainty", "Direct visibility into Bank X internal switch queuing telemetry is unavailable via payment gateway API.");
            root.put("requiresHumanReview", false);
            return root.toString();
        }

        // Scenario 2: Merchant-Specific Degradation
        if (evidence.incidentType() == IncidentType.MERCHANT_DEGRADATION || evidence.targetMerchantId() != null) {
            String merchant = evidence.targetMerchantId() != null ? evidence.targetMerchantId() : "merchant_hot";
            root.put("incidentType", "MERCHANT_DEGRADATION");
            root.put("summary", String.format("Root-cause investigation confirms integration or product configuration failure isolated strictly to merchant %s.", merchant));
            root.put("strongestRootCause", String.format("Integration, payload or credential failure isolated strictly to merchant %s", merchant));
            root.put("overallConfidence", 0.91);
            root.put("affectedSegment", "Merchant " + merchant);

            ArrayNode hypotheses = root.putArray("hypotheses");

            // Hypothesis A
            ObjectNode hA = hypotheses.addObject();
            hA.put("hypothesis", "Merchant API / Webhook Integration Misconfiguration");
            hA.put("confidence", 0.91);
            ArrayNode hASupp = hA.putArray("supportingEvidence");
            hASupp.add(String.format("100%% of anomalous failed transactions originate from merchant %s", merchant));
            hASupp.add("Failures occur across multiple independent banking institutions");
            hASupp.add("High concentration of ISSUER_DECLINED and INSUFFICIENT_FUNDS across multi-rail checkouts");
            ArrayNode hACont = hA.putArray("contradictingEvidence");
            hA.put("affectedSegment", "Merchant " + merchant);
            hA.put("verdict", "STRONGEST");

            // Hypothesis B
            ObjectNode hB = hypotheses.addObject();
            hB.put("hypothesis", "Multi-Bank Core Infrastructure Outage");
            hB.put("confidence", 0.15);
            ArrayNode hBSupp = hB.putArray("supportingEvidence");
            ArrayNode hBCont = hB.putArray("contradictingEvidence");
            hBCont.add("Other merchants transacting with the same banks show nominal baseline success rates");
            hB.put("affectedSegment", "Multi-Bank");
            hB.put("verdict", "REFUTED");

            // Hypothesis C
            ObjectNode hC = hypotheses.addObject();
            hC.put("hypothesis", "Gateway-wide Network Degradation");
            hC.put("confidence", 0.10);
            ArrayNode hCSupp = hC.putArray("supportingEvidence");
            ArrayNode hCCont = hC.putArray("contradictingEvidence");
            hCCont.add("Zero system-wide latency or timeout spikes detected across other merchant traffic");
            hC.put("affectedSegment", "Gateway");
            hC.put("verdict", "REFUTED");

            // Hypothesis D
            ObjectNode hD = hypotheses.addObject();
            hD.put("hypothesis", "Random Traffic Noise");
            hD.put("confidence", 0.05);
            ArrayNode hDSupp = hD.putArray("supportingEvidence");
            ArrayNode hDCont = hD.putArray("contradictingEvidence");
            hDCont.add(String.format("Merchant failure rate (%.2f%%) deviates massively from baseline", evidence.currentFailureRate()));
            hD.put("affectedSegment", "Merchant " + merchant);
            hD.put("verdict", "REFUTED");

            ArrayNode supp = root.putArray("supportingEvidence");
            supp.add(String.format("100%% of failures isolated to merchant %s", merchant));
            supp.add("Failures span multiple diverse banking rails");
            supp.add("Other merchants using the same rails remain healthy");

            ArrayNode cont = root.putArray("contradictingEvidence");
            cont.add("No upstream bank outage notifications on record");

            root.put("reasoning", String.format(
                    "Telemetry demonstrates that anomalous failures are 100%% concentrated on merchant %s. Because the failures occur across multiple banks simultaneously, banking rail degradation is refuted. Other merchants transacting with identical banks experience healthy success rates, proving the fault resides in the merchant checkout or integration configuration.",
                    merchant
            ));
            root.put("uncertainty", "Merchant backend server logs and API request headers cannot be directly inspected by Sentinel.");
            root.put("requiresHumanReview", true);
            return root.toString();
        }

        // Scenario 3: System / Normal Random Failures
        root.put("incidentType", evidence.incidentType() != null ? evidence.incidentType().name() : "SYSTEM_DEGRADATION");
        root.put("summary", "Root-cause investigation confirms diffuse background failure variance without a systemic point of failure.");
        root.put("strongestRootCause", "Diffuse stochastic baseline variation across multi-bank payment infrastructure");
        root.put("overallConfidence", 0.62);
        root.put("affectedSegment", "Global Payment System");

        ArrayNode hypotheses = root.putArray("hypotheses");

        // Hypothesis A
        ObjectNode hA = hypotheses.addObject();
        hA.put("hypothesis", "Diffuse Stochastic Baseline Fluctuation");
        hA.put("confidence", 0.62);
        ArrayNode hASupp = hA.putArray("supportingEvidence");
        hASupp.add("No single bank or merchant exceeds 40% failure concentration");
        hASupp.add(String.format("Failure rate increase is modest (+%.2f pts)", evidence.failureRateIncrease()));
        hASupp.add("Error codes reflect standard consumer drop-offs (INSUFFICIENT_FUNDS, USER_DROPPED)");
        ArrayNode hACont = hA.putArray("contradictingEvidence");
        hACont.add("Slight variation in short-window failure counts");
        hA.put("affectedSegment", "Global Payment System");
        hA.put("verdict", "STRONGEST");

        // Hypothesis B
        ObjectNode hB = hypotheses.addObject();
        hB.put("hypothesis", "Single Bank Infrastructure Outage");
        hB.put("confidence", 0.20);
        ArrayNode hBSupp = hB.putArray("supportingEvidence");
        ArrayNode hBCont = hB.putArray("contradictingEvidence");
        hBCont.add("Failure distribution across banks matches baseline market share");
        hB.put("affectedSegment", "Specific Banks");
        hB.put("verdict", "UNLIKELY");

        // Hypothesis C
        ObjectNode hC = hypotheses.addObject();
        hC.put("hypothesis", "Merchant Integration Outage");
        hC.put("confidence", 0.15);
        ArrayNode hCSupp = hC.putArray("supportingEvidence");
        ArrayNode hCCont = hC.putArray("contradictingEvidence");
        hCCont.add("Failures are distributed evenly across merchants");
        hC.put("affectedSegment", "Merchants");
        hC.put("verdict", "UNLIKELY");

        // Hypothesis D
        ObjectNode hD = hypotheses.addObject();
        hD.put("hypothesis", "Central Payment Switch Failure");
        hD.put("confidence", 0.08);
        ArrayNode hDSupp = hD.putArray("supportingEvidence");
        ArrayNode hDCont = hD.putArray("contradictingEvidence");
        hDCont.add("Gateway latency and success rates remain well within nominal operating tolerance");
        hD.put("affectedSegment", "Core Switch");
        hD.put("verdict", "REFUTED");

        ArrayNode supp = root.putArray("supportingEvidence");
        supp.add("Even distribution across all banks and payment methods");
        supp.add(String.format("Failure increase is minor (+%.2f pts)", evidence.failureRateIncrease()));

        ArrayNode cont = root.putArray("contradictingEvidence");
        cont.add("Slight statistical variance in recent window");

        root.put("reasoning", String.format(
                "Telemetry shows no single dimension (bank, payment method, error code, merchant) dominating the failure population. The observed fluctuation (+%.2f pts) is consistent with stochastic variations in consumer payment activity rather than a systemic technical failure.",
                evidence.failureRateIncrease()
        ));
        root.put("uncertainty", "Traffic sample in narrow time windows is subject to natural Poisson variance; continuous passive monitoring is advised.");
        root.put("requiresHumanReview", false);
        return root.toString();
    }

    @Override
    public String generateRecoveryStrategyJson(AiEvidenceDto evidence, String diagnosisSummary, String investigationSummary, String systemInstruction) {
        ObjectNode root = objectMapper.createObjectNode();

        if (evidence == null) {
            root.put("selectedStrategy", RecoveryStrategyType.HUMAN_REVIEW.name());
            root.put("rank", 1);
            root.put("rationale", "Insufficient telemetry to evaluate recovery feasibility. Human commander review required.");
            root.put("confidence", 0.2);
            root.put("riskLevel", RiskLevel.HIGH.name());
            root.put("affectedSegment", "UNKNOWN");
            root.put("expectedRecoveryPercentage", 0.0);
            root.put("requiresHumanApproval", true);

            ObjectNode stop = root.putObject("stoppingConditions");
            stop.put("maxRetryAttempts", 0);
            stop.put("retryWindowMinutes", 0);
            stop.put("backoffBaseSeconds", 0);
            stop.put("stopOnSuccess", false);
            stop.put("stopOnIncidentResolved", true);
            stop.put("failureRateThreshold", 0.0);
            stop.put("escalateOnFailureLimit", true);
            ArrayNode conds = stop.putArray("conditions");
            conds.add("Automated recovery locked pending human operator assessment");

            root.putArray("alternatives");
            return root.toString();
        }

        // Scenario 1: Bank X UPI Degradation
        if ("Bank X".equals(evidence.targetBank()) || (evidence.concentrationSummary() != null && "Bank X".equals(evidence.concentrationSummary().dominantBank()) && PaymentMethod.UPI.name().equals(evidence.concentrationSummary().dominantPaymentMethod()))) {
            root.put("selectedStrategy", RecoveryStrategyType.DELAYED_RETRY.name());
            root.put("rank", 1);
            root.put("rationale", "Immediate retries are likely to encounter the same upstream Bank X UPI switch timeout. A delayed retry with bounded exponential backoff allows transient congestion to clear while recovering affected payments.");
            root.put("confidence", 0.88);
            root.put("riskLevel", RiskLevel.MEDIUM.name());
            root.put("affectedSegment", "Bank X + UPI");
            root.put("expectedRecoveryPercentage", 70.0);
            root.put("requiresHumanApproval", false);

            ObjectNode stop = root.putObject("stoppingConditions");
            stop.put("maxRetryAttempts", 2);
            stop.put("retryWindowMinutes", 30);
            stop.put("backoffBaseSeconds", 30);
            stop.put("stopOnSuccess", true);
            stop.put("stopOnIncidentResolved", true);
            stop.put("failureRateThreshold", 50.0);
            stop.put("escalateOnFailureLimit", true);
            ArrayNode conds = stop.putArray("conditions");
            conds.add("Maximum 2 retry attempts per affected payment");
            conds.add("Retry window capped at 30 minutes");
            conds.add("Exponential backoff starting at 30 seconds");
            conds.add("Stop immediately after successful payment settlement");
            conds.add("Stop if incident is marked resolved");
            conds.add("Abort if downstream retry failure rate exceeds 50.0%");
            conds.add("Escalate to human review if attempts exceed limit without resolution");

            ArrayNode alternatives = root.putArray("alternatives");
            ObjectNode alt1 = alternatives.addObject();
            alt1.put("strategy", RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD.name());
            alt1.put("rank", 2);
            alt1.put("reason", "Reroute affected payments to alternate healthy payment methods (e.g. Netbanking / Cards) upon customer re-prompt.");
            alt1.put("expectedRecoveryPercentage", 60.0);
            alt1.put("riskLevel", RiskLevel.MEDIUM.name());

            ObjectNode alt2 = alternatives.addObject();
            alt2.put("strategy", RecoveryStrategyType.MONITOR_ONLY.name());
            alt2.put("rank", 3);
            alt2.put("reason", "Passive telemetry observation without active recovery; avoids customer contact but forfeits recoverable volume.");
            alt2.put("expectedRecoveryPercentage", 20.0);
            alt2.put("riskLevel", RiskLevel.LOW.name());

            return root.toString();
        }

        // Scenario 2: Merchant-Specific Degradation
        if (evidence.incidentType() == IncidentType.MERCHANT_DEGRADATION || evidence.targetMerchantId() != null) {
            String merchant = evidence.targetMerchantId() != null ? evidence.targetMerchantId() : "merchant_hot";
            root.put("selectedStrategy", RecoveryStrategyType.HUMAN_REVIEW.name());
            root.put("rank", 1);
            root.put("rationale", String.format("Failures are 100%% concentrated on merchant %s with integration/payload errors. Automated payment retries against a broken merchant endpoint will worsen error rates without recovering funds.", merchant));
            root.put("confidence", 0.90);
            root.put("riskLevel", RiskLevel.HIGH.name());
            root.put("affectedSegment", "Merchant " + merchant);
            root.put("expectedRecoveryPercentage", 30.0);
            root.put("requiresHumanApproval", true);

            ObjectNode stop = root.putObject("stoppingConditions");
            stop.put("maxRetryAttempts", 0);
            stop.put("retryWindowMinutes", 0);
            stop.put("backoffBaseSeconds", 0);
            stop.put("stopOnSuccess", false);
            stop.put("stopOnIncidentResolved", false);
            stop.put("failureRateThreshold", 0.0);
            stop.put("escalateOnFailureLimit", true);
            ArrayNode conds = stop.putArray("conditions");
            conds.add("Automated recovery strictly locked pending human operator engagement with merchant technical contact");
            conds.add("No automated payment mutations allowed");
            conds.add("Requires explicit approval by Payment Operations Commander");

            ArrayNode alternatives = root.putArray("alternatives");
            ObjectNode alt1 = alternatives.addObject();
            alt1.put("strategy", RecoveryStrategyType.MONITOR_ONLY.name());
            alt1.put("rank", 2);
            alt1.put("reason", "Monitor merchant integration error rates passively until merchant deploys fix.");
            alt1.put("expectedRecoveryPercentage", 15.0);
            alt1.put("riskLevel", RiskLevel.LOW.name());

            ObjectNode alt2 = alternatives.addObject();
            alt2.put("strategy", RecoveryStrategyType.NO_ACTION.name());
            alt2.put("rank", 3);
            alt2.put("reason", "Take no gateway-level action as the fault lies outside payment rail infrastructure.");
            alt2.put("expectedRecoveryPercentage", 0.0);
            alt2.put("riskLevel", RiskLevel.LOW.name());

            return root.toString();
        }

        // Scenario 3: System / Normal Random Failures
        root.put("selectedStrategy", RecoveryStrategyType.MONITOR_ONLY.name());
        root.put("rank", 1);
        root.put("rationale", "Observed failure variation is diffuse and consistent with normal baseline noise. Active recovery intervention is not justified and risks customer disruption.");
        root.put("confidence", 0.75);
        root.put("riskLevel", RiskLevel.LOW.name());
        root.put("affectedSegment", "Global Payment System");
        root.put("expectedRecoveryPercentage", 20.0);
        root.put("requiresHumanApproval", false);

        ObjectNode stop = root.putObject("stoppingConditions");
        stop.put("maxRetryAttempts", 0);
        stop.put("retryWindowMinutes", 60);
        stop.put("backoffBaseSeconds", 0);
        stop.put("stopOnSuccess", false);
        stop.put("stopOnIncidentResolved", true);
        stop.put("failureRateThreshold", 0.0);
        stop.put("escalateOnFailureLimit", true);
        ArrayNode conds = stop.putArray("conditions");
        conds.add("Zero automated retry or rerouting attempts");
        conds.add("Continuous passive telemetry monitoring for 60 minutes");
        conds.add("Stop when baseline failure rate returns below anomaly threshold");
        conds.add("Escalate to active diagnosis if failure rate escalates above 15%");

        ArrayNode alternatives = root.putArray("alternatives");
        ObjectNode alt1 = alternatives.addObject();
        alt1.put("strategy", RecoveryStrategyType.NO_ACTION.name());
        alt1.put("rank", 2);
        alt1.put("reason", "Standard baseline noise requires no operational changes.");
        alt1.put("expectedRecoveryPercentage", 0.0);
        alt1.put("riskLevel", RiskLevel.LOW.name());

        ObjectNode alt2 = alternatives.addObject();
        alt2.put("strategy", RecoveryStrategyType.HUMAN_REVIEW.name());
        alt2.put("rank", 3);
        alt2.put("reason", "Escalate to payment operations if telemetry shows unexpected sustained variance.");
        alt2.put("expectedRecoveryPercentage", 10.0);
        alt2.put("riskLevel", RiskLevel.LOW.name());

        return root.toString();
    }
}
