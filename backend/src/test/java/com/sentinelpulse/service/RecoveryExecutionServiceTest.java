package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.dto.AppliedLimitDto;
import com.sentinelpulse.dto.RecoveryExecutionResponse;
import com.sentinelpulse.dto.StoppingConditionsDto;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.entity.PolicyEvaluation;
import com.sentinelpulse.entity.RecoveryExecution;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.RecoveryOutcome;
import com.sentinelpulse.recovery.StoppingReason;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
import com.sentinelpulse.repository.IncidentInvestigationRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PolicyEvaluationRepository;
import com.sentinelpulse.repository.RecoveryExecutionAttemptRepository;
import com.sentinelpulse.repository.RecoveryExecutionRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecoveryExecutionServiceTest {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private RecoveryStrategyRepository recoveryStrategyRepository;

    @Autowired
    private PolicyEvaluationRepository policyEvaluationRepository;

    @Autowired
    private RecoveryExecutionRepository recoveryExecutionRepository;

    @Autowired
    private RecoveryExecutionAttemptRepository recoveryExecutionAttemptRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FinancialImpactService financialImpactService;

    @Autowired
    private ObjectMapper objectMapper;

    private RecoveryExecutionService recoveryExecutionService;

    @BeforeEach
    void setUp() {
        recoveryExecutionAttemptRepository.deleteAll();
        recoveryExecutionRepository.deleteAll();
        policyEvaluationRepository.deleteAll();
        recoveryStrategyRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();

        recoveryExecutionService = new RecoveryExecutionService(
                incidentRepository,
                recoveryStrategyRepository,
                policyEvaluationRepository,
                recoveryExecutionRepository,
                recoveryExecutionAttemptRepository,
                paymentRepository,
                financialImpactService,
                objectMapper
        );
    }

    private Incident createTestIncident(IncidentType type, IncidentStatus status, String bank,
                                        PaymentMethod method, String merchant, Instant startTime) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                type,
                IncidentSeverity.HIGH,
                now,
                startTime != null ? startTime : now.minusSeconds(3600),
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
                                                StoppingConditionsDto stoppingConditions) {
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
                Instant.now(),
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

    private PolicyEvaluation createTestPolicyEvaluation(Long incidentId,
                                                        Long strategyId,
                                                        PolicyDecision decision,
                                                        boolean overallAllowed,
                                                        boolean executionAllowed,
                                                        boolean requiredHumanApproval,
                                                        List<AppliedLimitDto> appliedLimits) {
        String limitsJson = "[]";
        if (appliedLimits != null) {
            try {
                limitsJson = objectMapper.writeValueAsString(appliedLimits);
            } catch (Exception ignored) {
            }
        }

        PolicyEvaluation policy = new PolicyEvaluation(
                incidentId,
                strategyId,
                Instant.now(),
                "POLICY_V1",
                decision,
                overallAllowed,
                executionAllowed,
                requiredHumanApproval,
                RiskLevel.MEDIUM,
                100.0,
                "Test policy evaluation summary",
                executionAllowed ? null : "Policy blocked execution",
                "Next step guidance",
                "[]",
                limitsJson,
                "[]",
                "[]"
        );
        return policyEvaluationRepository.save(policy);
    }

    private List<Payment> createTestPayments(String bank, PaymentMethod method, String merchant,
                                             Instant timestamp, int count, ErrorCode errorCode) {
        List<Payment> list = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            Payment p = new Payment(
                    merchant != null ? merchant : "merchant_1",
                    "cust_" + i,
                    BigDecimal.valueOf(1000L * i),
                    "INR",
                    timestamp != null ? timestamp : Instant.now().minusSeconds(1800),
                    method != null ? method : PaymentMethod.UPI,
                    bank != null ? bank : "Bank X",
                    PaymentStatus.FAILED,
                    errorCode != null ? errorCode : ErrorCode.BANK_TIMEOUT
            );
            list.add(p);
        }
        return paymentRepository.saveAll(list);
    }

    @Test
    @DisplayName("Test 1: Approved Strategy Executes with actual recovered payments and revenue")
    void testApprovedStrategy_executesSuccessfullyWithRecoveredAmount() {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, startTime);
        createTestPayments("Bank X", PaymentMethod.UPI, null, startTime.plusSeconds(300), 10, ErrorCode.BANK_TIMEOUT);

        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("Max 2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true, true, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(response.executionAllowed()).isTrue();
        assertThat(response.eligibleTransactions()).isEqualTo(10L);
        assertThat(response.attemptedTransactions()).isGreaterThanOrEqualTo(10L);
        assertThat(response.successfulRecoveries()).isGreaterThan(0L);
        assertThat(response.recoveredAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.recoveredAmount()).isLessThanOrEqualTo(response.authoritativeRecoverableAmount());
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.COMPLETED_ALL_ELIGIBLE);
        assertThat(response.attempts()).isNotEmpty();
    }

    @Test
    @DisplayName("Test 2: Approved With Limits strictly enforces clamped max retry attempts (e.g. 3 attempts)")
    void testApprovedWithLimits_enforcesClampedRetryLimit() {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, startTime);
        // Create payments that permanently fail
        createTestPayments("Bank X", PaymentMethod.UPI, null, startTime.plusSeconds(300), 3, ErrorCode.INSUFFICIENT_FUNDS);

        // Strategy proposed 10 retries
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                10, 30, 30, true, true, 50.0, true, List.of("10 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions
        );

        // Policy Gate clamped to 3 attempts
        List<AppliedLimitDto> appliedLimits = List.of(new AppliedLimitDto(
                "maxRetryAttempts", "10", "3", "Clamped to policy max 3"
        ));
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED_WITH_LIMITS, true, true, false, appliedLimits);

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(response.appliedRetryLimit()).isEqualTo(3);
        // Verify no single payment had attemptNumber > 3
        assertThat(response.attempts()).allMatch(a -> a.attemptNumber() <= 3);
    }

    @Test
    @DisplayName("Test 3: HUMAN_REVIEW Policy decision blocks autonomous execution")
    void testHumanReview_blocksExecution() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, null);
        createTestPayments("Bank X", PaymentMethod.UPI, null, Instant.now().minusSeconds(1000), 5, ErrorCode.BANK_TIMEOUT);

        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.HIGH, 0.65,
                BigDecimal.valueOf(25_000), 50.0, true, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.HUMAN_REVIEW, true, false, true, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.BLOCKED);
        assertThat(response.executionAllowed()).isFalse();
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.HUMAN_APPROVAL_REQUIRED);
        assertThat(response.attemptedTransactions()).isEqualTo(0L);
        assertThat(response.recoveredAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Test 4: REJECTED Policy decision blocks execution")
    void testRejectedPolicy_blocksExecution() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(25_000), 50.0, false, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.REJECTED, false, false, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.BLOCKED);
        assertThat(response.executionAllowed()).isFalse();
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.POLICY_BLOCKED);
        assertThat(response.attemptedTransactions()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Test 5: Critical Risk Policy blocks execution")
    void testCriticalRiskPolicy_blocksExecution() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.CRITICAL, 0.90,
                BigDecimal.valueOf(25_000), 50.0, false, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.REJECTED, false, false, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.BLOCKED);
        assertThat(response.executionAllowed()).isFalse();
    }

    @Test
    @DisplayName("Test 6: Merchant Degradation fails retry attempts deterministically")
    void testMerchantDegradation_retriesDoNotSucceed() {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(IncidentType.MERCHANT_DEGRADATION, IncidentStatus.DETECTED, null, null, "merchant_broken", startTime);
        createTestPayments(null, null, "merchant_broken", startTime.plusSeconds(300), 5, ErrorCode.BANK_TIMEOUT);

        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.85,
                BigDecimal.valueOf(15_000), 50.0, false, StrategyStatus.PROPOSED, conditions
        );
        // Force approval to test simulation logic
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true, true, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.COMPLETED);
        // Merchant degradation retries must not recover
        assertThat(response.successfulRecoveries()).isEqualTo(0L);
        assertThat(response.recoveredAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Test 7: ALTERNATE_PAYMENT_METHOD Strategy requiring human review is BLOCKED")
    void testAlternatePaymentMethod_requiringHumanReviewIsBlocked() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                1, 15, 0, true, true, 40.0, true, List.of("1 reroute attempt")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD, RiskLevel.MEDIUM, 0.85,
                BigDecimal.valueOf(25_000), 50.0, true, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.HUMAN_REVIEW, true, false, true, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.BLOCKED);
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.HUMAN_APPROVAL_REQUIRED);
    }

    @Test
    @DisplayName("Test 8: Circuit Breaker halts execution when failure rate exceeds threshold")
    void testCircuitBreaker_haltsExecutionOnHighFailureRate() {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, startTime);
        // Create 20 payments with permanent non-recoverable errors
        createTestPayments("Bank X", PaymentMethod.UPI, null, startTime.plusSeconds(300), 20, ErrorCode.INSUFFICIENT_FUNDS);

        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("Circuit breaker 50%")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true, true, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.STOPPED);
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.CIRCUIT_BREAKER_FAILURE_RATE);
        assertThat(response.attemptedTransactions()).isLessThan(20L);
        assertThat(response.skippedTransactions()).isGreaterThan(0L);
    }

    @Test
    @DisplayName("Test 9: Resolved Incident blocks recovery execution")
    void testResolvedIncident_blocksExecution() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.RESOLVED, "Bank X", PaymentMethod.UPI, null, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(20_000), 50.0, false, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true, true, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.BLOCKED);
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.INCIDENT_RESOLVED);
    }

    @Test
    @DisplayName("Test 10: Strategy / Policy ID mismatch blocks execution")
    void testStrategyMismatch_blocksExecution() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, null);
        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.LOW, 0.90,
                BigDecimal.valueOf(20_000), 50.0, false, StrategyStatus.PROPOSED, conditions
        );
        // Policy evaluation refers to a different strategyId (9999L)
        createTestPolicyEvaluation(incident.getId(), 9999L, PolicyDecision.APPROVED, true, true, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.BLOCKED);
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.POLICY_BLOCKED);
    }

    @Test
    @DisplayName("Test 11: Idempotency returns existing execution without double-counting")
    void testIdempotency_returnsExistingExecution() {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, startTime);
        createTestPayments("Bank X", PaymentMethod.UPI, null, startTime.plusSeconds(300), 5, ErrorCode.BANK_TIMEOUT);

        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true, true, false, List.of());

        // First run
        RecoveryExecutionResponse firstRun = recoveryExecutionService.executeRecovery(incident.getId());
        assertThat(firstRun.status()).isEqualTo(ExecutionStatus.COMPLETED);

        // Second run
        RecoveryExecutionResponse secondRun = recoveryExecutionService.executeRecovery(incident.getId());
        assertThat(secondRun.executionId()).isEqualTo(firstRun.executionId());
        assertThat(secondRun.recoveredAmount()).isEqualByComparingTo(firstRun.recoveredAmount());
    }

    @Test
    @DisplayName("Test 12: Financial bound guarantees recoveredAmount never exceeds authoritative recoverable revenue")
    void testFinancialBound_neverExceedsAuthoritativeRecoverable() {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, IncidentStatus.DETECTED, "Bank X", PaymentMethod.UPI, null, startTime);
        createTestPayments("Bank X", PaymentMethod.UPI, null, startTime.plusSeconds(300), 10, ErrorCode.BANK_TIMEOUT);

        StoppingConditionsDto conditions = new StoppingConditionsDto(
                2, 30, 30, true, true, 50.0, true, List.of("2 retries")
        );
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.DELAYED_RETRY, RiskLevel.MEDIUM, 0.88,
                BigDecimal.valueOf(35_000), 70.0, false, StrategyStatus.PROPOSED, conditions
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true, true, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.recoveredAmount()).isLessThanOrEqualTo(response.authoritativeRecoverableAmount());
        assertThat(response.remainingRecoverableAmount()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Test 13: MONITOR_ONLY Strategy completes as no-op with 0 attempts and 0 recovered")
    void testMonitorOnly_completesAsNoOp() {
        Incident incident = createTestIncident(IncidentType.SYSTEM_DEGRADATION, IncidentStatus.DETECTED, null, null, null, null);
        RecoveryStrategy strategy = createTestStrategy(
                incident, RecoveryStrategyType.MONITOR_ONLY, RiskLevel.LOW, 0.88,
                BigDecimal.ZERO, 0.0, false, StrategyStatus.PROPOSED, null
        );
        createTestPolicyEvaluation(incident.getId(), strategy.getId(), PolicyDecision.APPROVED, true, true, false, List.of());

        RecoveryExecutionResponse response = recoveryExecutionService.executeRecovery(incident.getId());

        assertThat(response.status()).isEqualTo(ExecutionStatus.COMPLETED);
        assertThat(response.attemptedTransactions()).isEqualTo(0L);
        assertThat(response.successfulRecoveries()).isEqualTo(0L);
        assertThat(response.recoveredAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.stoppingReason()).isEqualTo(StoppingReason.NO_ACTION_REQUIRED);
    }
}
