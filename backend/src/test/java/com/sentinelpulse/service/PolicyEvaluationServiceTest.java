package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.dto.AppliedLimitDto;
import com.sentinelpulse.dto.PolicyCheckDto;
import com.sentinelpulse.dto.PolicyEvaluationResponse;
import com.sentinelpulse.dto.StoppingConditionsDto;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.policy.PolicyCheckResult;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
import com.sentinelpulse.repository.IncidentInvestigationRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PolicyEvaluationRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PolicyEvaluationServiceTest {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private RecoveryStrategyRepository recoveryStrategyRepository;

    @Autowired
    private PolicyEvaluationRepository policyEvaluationRepository;

    @Autowired
    private FinancialImpactService financialImpactService;

    @Autowired
    private IncidentEvidenceService incidentEvidenceService;

    @Autowired
    private IncidentInvestigationRepository incidentInvestigationRepository;

    @Autowired
    private IncidentDiagnosisRepository incidentDiagnosisRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGeneratorService paymentGeneratorService;

    @Autowired
    private ObjectMapper objectMapper;

    private PolicyEvaluationService policyEvaluationService;

    @BeforeEach
    void setUp() {
        policyEvaluationRepository.deleteAll();
        recoveryStrategyRepository.deleteAll();
        incidentInvestigationRepository.deleteAll();
        incidentDiagnosisRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();

        policyEvaluationService = new PolicyEvaluationService(
                incidentRepository,
                recoveryStrategyRepository,
                financialImpactService,
                incidentEvidenceService,
                incidentInvestigationRepository,
                incidentDiagnosisRepository,
                policyEvaluationRepository,
                objectMapper
        );
    }

    private Incident createTestIncident(IncidentType type, IncidentStatus status, String bank,
                                        PaymentMethod method, String merchant) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                type,
                IncidentSeverity.HIGH,
                now,
                now.minusSeconds(3600),
                100,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(60_000),
                BigDecimal.valueOf(50_000),
                88.0,
                status,
                3.5,
                78.5,
                75.0,
                bank,
                method,
                merchant,
                60
        );
        return incidentRepository.save(incident);
    }

    private RecoveryStrategy createTestStrategy(Incident incident,
                                                RecoveryStrategyType strategyType,
                                                RiskLevel riskLevel,
                                                double confidence,
                                                BigDecimal estimatedRecovery,
                                                Double recoveryPct,
                                                Boolean requiresHumanApproval,
                                                StrategyStatus status,
                                                StoppingConditionsDto stoppingConditions,
                                                Instant createdAt) {
        String stoppingJson = null;
        if (stoppingConditions != null) {
            try {
                stoppingJson = objectMapper.writeValueAsString(stoppingConditions);
            } catch (Exception ignored) {
            }
        }

        RecoveryStrategy strategy = new RecoveryStrategy(
                incident.getId(),
                null,
                createdAt != null ? createdAt : Instant.now(),
                status,
                strategyType,
                1,
                "Test strategy rationale for " + strategyType,
                confidence,
                riskLevel,
                incident.getTargetBank() != null ? incident.getTargetBank() + " + UPI" : "Global",
                incident.getRevenueAtRisk(),
                BigDecimal.valueOf(50_000),
                recoveryPct != null ? recoveryPct : 70.0,
                estimatedRecovery != null ? estimatedRecovery : BigDecimal.valueOf(35_000),
                50L,
                stoppingJson,
                requiresHumanApproval != null ? requiresHumanApproval : false,
                "[]",
                StrategySource.DETERMINISTIC_RULES,
                "TEST_MODEL",
                null
        );
        return recoveryStrategyRepository.save(strategy);
    }

    @Test
    @DisplayName("Test 1: Safe Bank X UPI Strategy is APPROVED or APPROVED_WITH_LIMITS with execution allowed")
    void testSafeBankXUpiStrategy_isApprovedOrApprovedWithLimits() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("Max 2 retries", "Stop on success")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.85,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.incidentId()).isEqualTo(incident.getId());
        assertThat(response.evaluatorVersion()).isEqualTo("POLICY_V1");
        assertThat(response.decision()).isIn(PolicyDecision.APPROVED, PolicyDecision.APPROVED_WITH_LIMITS);
        assertThat(response.overallAllowed()).isTrue();
        assertThat(response.executionAllowed()).isTrue();
        assertThat(response.requiredHumanApproval()).isFalse();
        assertThat(response.violations()).isEmpty();
        assertThat(response.policyChecks()).isNotEmpty();
    }

    @Test
    @DisplayName("Test 2: Retry Limit Violation is clamped to MAX 3 attempts and results in APPROVED_WITH_LIMITS")
    void testRetryLimitViolation_clampsToMaxAllowedAttempts() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto excessiveConditions = new StoppingConditionsDto(
                10, 30, 30, true, true, 50.0, true, List.of("Aggressive 10 retries")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, excessiveConditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.APPROVED_WITH_LIMITS);
        assertThat(response.overallAllowed()).isTrue();
        assertThat(response.executionAllowed()).isTrue();
        assertThat(response.appliedLimits()).isNotEmpty();
        assertThat(response.appliedLimits()).anyMatch(limit ->
                "maxRetryAttempts".equals(limit.parameter()) && "3".equals(limit.enforcedValue()));
        assertThat(response.warnings()).anyMatch(w -> w.contains("clamped"));
    }

    @Test
    @DisplayName("Test 3: CRITICAL Risk Level results in REJECTED for autonomous recovery")
    void testCriticalRisk_isRejectedForAutonomousRecovery() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("Normal retries")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.CRITICAL, 0.90,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.REJECTED);
        assertThat(response.overallAllowed()).isFalse();
        assertThat(response.executionAllowed()).isFalse();
        assertThat(response.violations()).anyMatch(v -> v.toLowerCase().contains("critical risk"));
    }

    @Test
    @DisplayName("Test 4: Low Confidence (< 0.70) results in HUMAN_REVIEW")
    void testLowConfidence_resultsInHumanReview() {
        Incident incident = createTestIncident(IncidentType.SYSTEM_DEGRADATION, IncidentStatus.DETECTED, null, null, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                1, 15, 15, true, true, 50.0, true, List.of("1 retry")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.55,
                BigDecimal.valueOf(20_000), 40.0, false, StrategyStatus.PROPOSED, conditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.HUMAN_REVIEW);
        assertThat(response.overallAllowed()).isTrue();
        assertThat(response.executionAllowed()).isFalse();
        assertThat(response.requiredHumanApproval()).isTrue();
        assertThat(response.warnings()).anyMatch(w -> w.contains("Low confidence"));
    }

    @Test
    @DisplayName("Test 5: Merchant Degradation blocks automated retries and requires HUMAN_REVIEW")
    void testMerchantDegradation_blocksAutomatedRetries() {
        Incident incident = createTestIncident(IncidentType.MERCHANT_DEGRADATION, IncidentStatus.DETECTED, null, null, "merchant_broken");

        // Attempting an automated retry on a merchant issue -> REJECTED
        StoppingConditionsDto retryConditions = new StoppingConditionsDto(
                2, 20, 20, true, true, 50.0, true, List.of("2 retries")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.85,
                BigDecimal.valueOf(10_000), 50.0, false, StrategyStatus.PROPOSED, retryConditions, Instant.now()
        );

        PolicyEvaluationResponse retryResponse = policyEvaluationService.evaluatePolicy(incident.getId());
        assertThat(retryResponse.decision()).isEqualTo(PolicyDecision.REJECTED);
        assertThat(retryResponse.violations()).anyMatch(v -> v.toLowerCase().contains("merchant"));

        // When properly routed to HUMAN_REVIEW -> HUMAN_REVIEW
        RecoveryStrategy humanStrategy = createTestStrategy(
                incident, RecoveryStrategyType.HUMAN_REVIEW, RiskLevel.HIGH, 0.90,
                BigDecimal.ZERO, 0.0, true, StrategyStatus.PROPOSED, null, Instant.now()
        );

        PolicyEvaluationResponse humanResponse = policyEvaluationService.evaluatePolicy(incident.getId());
        assertThat(humanResponse.decision()).isEqualTo(PolicyDecision.HUMAN_REVIEW);
        assertThat(humanResponse.overallAllowed()).isTrue();
        assertThat(humanResponse.executionAllowed()).isFalse();
        assertThat(humanResponse.requiredHumanApproval()).isTrue();
    }

    @Test
    @DisplayName("Test 6: ALTERNATE_PAYMENT_METHOD Strategy requires HUMAN_REVIEW")
    void testAlternatePaymentMethod_requiresHumanReview() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                1, 15, 0, true, true, 40.0, true, List.of("Reroute attempt")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD, RiskLevel.MEDIUM, 0.85,
                BigDecimal.valueOf(30_000), 60.0, false, StrategyStatus.PROPOSED, conditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.HUMAN_REVIEW);
        assertThat(response.requiredHumanApproval()).isTrue();
        assertThat(response.executionAllowed()).isFalse();
        assertThat(response.warnings()).anyMatch(w -> w.toLowerCase().contains("rerouting"));
    }

    @Test
    @DisplayName("Test 7: Missing Stopping Conditions on executable strategy is REJECTED")
    void testMissingStoppingConditions_isRejected() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        // Null stopping conditions on executable strategy
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(20_000), 50.0, false, StrategyStatus.PROPOSED, null, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.REJECTED);
        assertThat(response.overallAllowed()).isFalse();
        assertThat(response.executionAllowed()).isFalse();
        assertThat(response.violations()).anyMatch(v -> v.toLowerCase().contains("missing mandatory stopping conditions"));
    }

    @Test
    @DisplayName("Test 8: Financial Bound Violation (> Authoritative Recoverable) is REJECTED")
    void testFinancialBoundViolation_isRejected() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 20, 20, true, true, 50.0, true, List.of("2 retries")
        );
        // Authoritative recoverable is around 50,000; proposing 999,999 exceeds truth
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(999_999), 70.0, false, StrategyStatus.PROPOSED, conditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.REJECTED);
        assertThat(response.violations()).anyMatch(v -> v.toLowerCase().contains("exceeds authoritative"));
    }

    @Test
    @DisplayName("Test 9: Resolved Incident is REJECTED")
    void testResolvedIncident_isRejected() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.RESOLVED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 20, 20, true, true, 50.0, true, List.of("2 retries")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(20_000), 50.0, false, StrategyStatus.PROPOSED, conditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.REJECTED);
        assertThat(response.violations()).anyMatch(v -> v.toLowerCase().contains("resolved incident"));
    }

    @Test
    @DisplayName("Test 10: Superseded Strategy is REJECTED")
    void testSupersededStrategy_isRejected() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 20, 20, true, true, 50.0, true, List.of("2 retries")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(20_000), 50.0, false, StrategyStatus.SUPERSEDED, conditions, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.REJECTED);
        assertThat(response.violations()).anyMatch(v -> v.toLowerCase().contains("eligible for policy approval"));
    }

    @Test
    @DisplayName("Test 11: Stale Strategy (> 30 mins) requires HUMAN_REVIEW")
    void testStaleStrategy_requiresHumanReview() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 20, 20, true, true, 50.0, true, List.of("2 retries")
        );
        Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(20_000), 50.0, false, StrategyStatus.PROPOSED, conditions, twoHoursAgo
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.HUMAN_REVIEW);
        assertThat(response.requiredHumanApproval()).isTrue();
        assertThat(response.warnings()).anyMatch(w -> w.contains("older than 30 minutes"));
    }

    @Test
    @DisplayName("Test 12: Normal Random Noise produces MONITOR_ONLY which is APPROVED without retry execution")
    void testNormalRandomNoise_producesApprovedMonitorOnly() {
        Incident incident = createTestIncident(IncidentType.SYSTEM_DEGRADATION, IncidentStatus.DETECTED, null, null, null);
        createTestStrategy(
                incident, RecoveryStrategyType.MONITOR_ONLY, RiskLevel.LOW, 0.88,
                BigDecimal.ZERO, 0.0, false, StrategyStatus.PROPOSED, null, Instant.now()
        );

        PolicyEvaluationResponse response = policyEvaluationService.evaluatePolicy(incident.getId());

        assertThat(response.decision()).isEqualTo(PolicyDecision.APPROVED);
        assertThat(response.overallAllowed()).isTrue();
        // Monitor-only strategy does not execute retries
        assertThat(response.executionAllowed()).isFalse();
        assertThat(response.violations()).isEmpty();
    }

    @Test
    @DisplayName("Test 13: Persistence stores evaluation and retrieval produces identical record")
    void testPersistenceAndRetrieval() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions, Instant.now()
        );

        PolicyEvaluationResponse evaluated = policyEvaluationService.evaluatePolicy(incident.getId());
        assertThat(evaluated.evaluationId()).isNotNull();

        Optional<PolicyEvaluationResponse> retrieved = policyEvaluationService.getLatestEvaluation(incident.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().evaluationId()).isEqualTo(evaluated.evaluationId());
        assertThat(retrieved.get().decision()).isEqualTo(evaluated.decision());
        assertThat(retrieved.get().evaluatorVersion()).isEqualTo("POLICY_V1");
        assertThat(retrieved.get().policyChecks()).hasSize(evaluated.policyChecks().size());
    }
}
