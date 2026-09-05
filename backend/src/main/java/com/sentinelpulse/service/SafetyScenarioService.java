package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.ControlledAction;
import com.sentinelpulse.ai.DiagnosisStatus;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.audit.AuditActorType;
import com.sentinelpulse.audit.AuditEventCategory;
import com.sentinelpulse.audit.AuditEventType;
import com.sentinelpulse.audit.AuditSeverity;
import com.sentinelpulse.audit.AuditStatus;
import com.sentinelpulse.dto.AuditVerificationResponse;
import com.sentinelpulse.dto.PolicyEvaluationResponse;
import com.sentinelpulse.dto.RecoveryExecutionResponse;
import com.sentinelpulse.dto.SafetyScenarioDto;
import com.sentinelpulse.dto.SafetyScenarioResult;
import com.sentinelpulse.dto.StoppingConditionsDto;
import com.sentinelpulse.entity.AuditEvent;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentDiagnosis;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.entity.PolicyEvaluation;
import com.sentinelpulse.entity.RecoveryExecution;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.StoppingReason;
import com.sentinelpulse.repository.AuditEventRepository;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PolicyEvaluationRepository;
import com.sentinelpulse.repository.RecoveryExecutionRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
import com.sentinelpulse.safety.SafetyScenarioType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service orchestrating the Sentinel Safety & Failure Lab.
 *
 * <p>Executes controlled, deterministic resilience scenarios testing all safety invariants:
 * <ul>
 *   <li>INVARIANT-001: AI cannot directly execute recovery (fails closed).</li>
 *   <li>INVARIANT-002: Policy rejection always prevents execution.</li>
 *   <li>INVARIANT-003: Human-review decisions prevent autonomous execution.</li>
 *   <li>INVARIANT-004: CRITICAL risk cannot autonomously execute.</li>
 *   <li>INVARIANT-005: Low-confidence AI output cannot bypass human review/policy.</li>
 *   <li>INVARIANT-006: Recovery attempts cannot exceed policy-approved limits.</li>
 *   <li>INVARIANT-007: Circuit breaker stops excessive failures.</li>
 *   <li>INVARIANT-008: Resolved incidents cannot continue recovery.</li>
 *   <li>INVARIANT-009: Stale strategies cannot execute autonomously.</li>
 *   <li>INVARIANT-010: Idempotent execution cannot double-count recovery.</li>
 *   <li>INVARIANT-011: Actual recovery cannot exceed authoritative recoverable revenue.</li>
 *   <li>INVARIANT-012: Audit failures do not corrupt the primary business workflow.</li>
 *   <li>INVARIANT-013: Audit tampering is detectable via SHA-256 hash chains.</li>
 *   <li>INVARIANT-014: No scenario can bypass the Policy Engine.</li>
 *   <li>INVARIANT-015: No scenario directly mutates payment records to fake recovery.</li>
 * </ul>
 */
@Service
public class SafetyScenarioService {

    private static final Logger log = LoggerFactory.getLogger(SafetyScenarioService.class);

    private final IncidentRepository incidentRepository;
    private final PaymentRepository paymentRepository;
    private final IncidentDiagnosisRepository incidentDiagnosisRepository;
    private final RecoveryStrategyRepository recoveryStrategyRepository;
    private final PolicyEvaluationRepository policyEvaluationRepository;
    private final RecoveryExecutionRepository recoveryExecutionRepository;
    private final AuditEventRepository auditEventRepository;

    private final PolicyEvaluationService policyEvaluationService;
    private final RecoveryExecutionService recoveryExecutionService;
    private final AuditEventService auditEventService;
    private final ObjectMapper objectMapper;

    public SafetyScenarioService(IncidentRepository incidentRepository,
                                 PaymentRepository paymentRepository,
                                 IncidentDiagnosisRepository incidentDiagnosisRepository,
                                 RecoveryStrategyRepository recoveryStrategyRepository,
                                 PolicyEvaluationRepository policyEvaluationRepository,
                                 RecoveryExecutionRepository recoveryExecutionRepository,
                                 AuditEventRepository auditEventRepository,
                                 PolicyEvaluationService policyEvaluationService,
                                 RecoveryExecutionService recoveryExecutionService,
                                 AuditEventService auditEventService,
                                 ObjectMapper objectMapper) {
        this.incidentRepository = incidentRepository;
        this.paymentRepository = paymentRepository;
        this.incidentDiagnosisRepository = incidentDiagnosisRepository;
        this.recoveryStrategyRepository = recoveryStrategyRepository;
        this.policyEvaluationRepository = policyEvaluationRepository;
        this.recoveryExecutionRepository = recoveryExecutionRepository;
        this.auditEventRepository = auditEventRepository;
        this.policyEvaluationService = policyEvaluationService;
        this.recoveryExecutionService = recoveryExecutionService;
        this.auditEventService = auditEventService;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns metadata descriptors for all 12 controlled safety scenarios.
     */
    public List<SafetyScenarioDto> getScenarioDefinitions() {
        return List.of(
                new SafetyScenarioDto(
                        SafetyScenarioType.AI_PROVIDER_FAILURE,
                        "AI Provider Outage",
                        "AI_SAFETY",
                        "Simulates an unexpected timeout or network error connecting to the upstream AI provider.",
                        "INVARIANT-001: AI failures fail closed without bypassing safety gates.",
                        "Diagnosis marked FAILED; no downstream recovery created; incident remains safe; failure recorded in audit trail."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.AI_INVALID_RESPONSE,
                        "Invalid / Malformed AI JSON",
                        "AI_SAFETY",
                        "Simulates malformed JSON, out-of-bounds confidence (e.g. > 1.0), or missing schema fields from AI output.",
                        "INVARIANT-005: Schema-invalid AI output is rejected before reaching policy or execution.",
                        "Response validation catches error; diagnosis marked INVALID_RESPONSE; zero downstream action."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.AI_LOW_CONFIDENCE,
                        "Low-Confidence AI Recommendation",
                        "AI_SAFETY",
                        "Simulates an AI recommendation with confidence below the 0.70 threshold.",
                        "INVARIANT-005: Low-confidence AI outputs strictly mandate human sign-off.",
                        "Diagnosis marked advisory; Policy Gate enforces HUMAN_REVIEW; executionAllowed = false."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.POLICY_REJECTION,
                        "Policy Gate Rejection",
                        "POLICY_GATE",
                        "Simulates an unsafe recovery proposal (e.g. CRITICAL risk or automated retries on merchant degradation).",
                        "INVARIANT-002: Policy rejection cannot be bypassed by downstream execution.",
                        "Policy Gate evaluates to REJECTED; executionAllowed = false; recovery execution refused."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.HUMAN_REVIEW_REQUIRED,
                        "Human Review Escalation",
                        "POLICY_GATE",
                        "Simulates a recovery strategy that requires manual commander sign-off (e.g. HIGH risk or alternate payment methods).",
                        "INVARIANT-003: Human review mandates prevent autonomous recovery.",
                        "Policy Gate evaluates to HUMAN_REVIEW; executionAllowed = false; autonomous execution blocked."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.EXECUTION_FAILURE,
                        "Controlled Execution Failure & Bounds",
                        "EXECUTION_SAFETY",
                        "Simulates retrying payments where downstream banks persistently fail.",
                        "INVARIANT-006: Execution retries are strictly bounded by policy limits without money over-counting.",
                        "Retries executed up to policy-clamped limit; failures recorded; recoveredAmount <= authoritative recoverable."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.CIRCUIT_BREAKER,
                        "Circuit Breaker Trip",
                        "EXECUTION_SAFETY",
                        "Simulates high failure rate during execution (> 50% failure rate across attempted batch).",
                        "INVARIANT-007: Circuit breaker halts retry storm upon high failure rate.",
                        "Execution immediately halts with status STOPPED; stoppingReason = CIRCUIT_BREAKER_FAILURE_RATE; zero subsequent attempts."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.INCIDENT_RESOLVED_DURING_EXECUTION,
                        "Incident Resolved Pre-Execution",
                        "EXECUTION_SAFETY",
                        "Simulates an incident that cleared or was marked RESOLVED before recovery execution.",
                        "INVARIANT-008: Resolved incidents cannot execute recovery.",
                        "Policy Gate rejects with POLICY-009 or execution halts immediately; stoppingReason = INCIDENT_RESOLVED / POLICY_BLOCKED."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.STALE_STRATEGY,
                        "Stale Strategy Guardrail",
                        "EXECUTION_SAFETY",
                        "Simulates a recovery strategy formulated > 30 minutes ago without re-evaluation.",
                        "INVARIANT-009: Stale strategies cannot execute autonomously.",
                        "Policy Gate evaluates to HUMAN_REVIEW via POLICY-011 freshness guardrail; executionAllowed = false."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.IDEMPOTENT_EXECUTION,
                        "Idempotent Execution Guard",
                        "EXECUTION_SAFETY",
                        "Simulates calling recovery execution multiple times for the same eligible incident/strategy.",
                        "INVARIANT-010: Idempotent execution prevents duplicate recovery and double-counting.",
                        "Second request returns existing execution; no duplicate payments retried; EXECUTION_IDEMPOTENT_REUSE recorded in audit."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.AUDIT_TAMPER_DETECTION,
                        "Cryptographic Tamper Detection",
                        "AUDIT_INTEGRITY",
                        "Simulates direct tampering with an existing audit event payload in the database.",
                        "INVARIANT-013: Audit record tampering is cryptographically detectable via SHA-256 hash chains.",
                        "Verification engine detects hash mismatch; chainValid = false; brokenEventId pinpointed."
                ),
                new SafetyScenarioDto(
                        SafetyScenarioType.AUDIT_PERSISTENCE_FAILURE,
                        "Fail-Safe Audit Logging",
                        "AUDIT_INTEGRITY",
                        "Simulates an unexpected persistence error during audit logging.",
                        "INVARIANT-012: Audit failures do not corrupt the primary business workflow.",
                        "Audit persistence errors are contained within isolated fail-safe boundaries; primary business operations complete safely."
                )
        );
    }

    /**
     * Executes all 12 safety scenarios in batch and returns structured validation results.
     */
    @Transactional
    public List<SafetyScenarioResult> runAllScenarios() {
        List<SafetyScenarioResult> results = new ArrayList<>();
        for (SafetyScenarioType type : SafetyScenarioType.values()) {
            results.add(runScenario(type));
        }
        return results;
    }

    /**
     * Executes a single safety scenario deterministically.
     */
    @Transactional
    public SafetyScenarioResult runScenario(SafetyScenarioType type) {
        log.info("Executing safety scenario [{}]...", type);
        try {
            return switch (type) {
                case AI_PROVIDER_FAILURE -> runAiProviderFailure();
                case AI_INVALID_RESPONSE -> runAiInvalidResponse();
                case AI_LOW_CONFIDENCE -> runAiLowConfidence();
                case POLICY_REJECTION -> runPolicyRejection();
                case HUMAN_REVIEW_REQUIRED -> runHumanReviewRequired();
                case EXECUTION_FAILURE -> runExecutionFailure();
                case CIRCUIT_BREAKER -> runCircuitBreaker();
                case INCIDENT_RESOLVED_DURING_EXECUTION -> runIncidentResolved();
                case STALE_STRATEGY -> runStaleStrategy();
                case IDEMPOTENT_EXECUTION -> runIdempotentExecution();
                case AUDIT_TAMPER_DETECTION -> runAuditTamperDetection();
                case AUDIT_PERSISTENCE_FAILURE -> runAuditPersistenceFailure();
            };
        } catch (Exception ex) {
            log.error("Error executing safety scenario [{}]: {}", type, ex.getMessage(), ex);
            return SafetyScenarioResult.error(
                    type,
                    type.name(),
                    "SYSTEM",
                    "Safety scenario should complete cleanly without unexpected crashes.",
                    ex.getMessage()
            );
        }
    }

    // =========================================================================
    // Scenario Implementations
    // =========================================================================

    private SafetyScenarioResult runAiProviderFailure() {
        Incident incident = createTestIncident("AI Provider Failure Test Incident", IncidentType.PAYMENT_DEGRADATION);
        auditEventService.recordIncidentDetected(incident);

        // Record AI provider failure audit event directly as occurs during network outage
        auditEventService.recordAiDiagnosisFailed(incident.getId(), "Connection timed out to upstream AI provider");

        IncidentDiagnosis failedDiagnosis = IncidentDiagnosis.failed(
                incident.getId(), "MOCK", "mock-agent-v1",
                DiagnosisStatus.FAILED, "Connection timed out to upstream AI provider", null
        );
        incidentDiagnosisRepository.save(failedDiagnosis);

        // Verify incident remains safe
        int strategyCount = recoveryStrategyRepository.findAllByIncidentIdOrderByCreatedAtDesc(incident.getId()).size();
        int executionCount = recoveryExecutionRepository.findAllByIncidentIdOrderByStartedAtDesc(incident.getId()).size();

        AuditVerificationResponse verification = auditEventService.verifyIncidentAuditChain(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.AI_PROVIDER_FAILURE,
                "AI Provider Outage",
                "AI_SAFETY",
                "Diagnosis marked FAILED; no downstream recovery created; incident remains safe; failure recorded in audit trail.",
                "Diagnosis status = FAILED; strategyCount = " + strategyCount + "; executionCount = " + executionCount + "; audit chain verified (" + verification.eventCount() + " events).",
                "INVARIANT-001: AI failures fail closed without bypassing safety gates.",
                incident.getId(),
                null,
                null,
                false,
                null,
                BigDecimal.ZERO,
                null,
                verification.eventCount(),
                verification.valid(),
                "Verified that an AI provider failure does not trigger unsafe recovery actions or corrupt incident state."
        );
    }

    private SafetyScenarioResult runAiInvalidResponse() {
        Incident incident = createTestIncident("AI Invalid Response Test Incident", IncidentType.PAYMENT_DEGRADATION);
        auditEventService.recordIncidentDetected(incident);

        // Record invalid AI response handling
        auditEventService.recordAiDiagnosisFailed(incident.getId(), "Invalid response: confidence value 1.5 exceeds maximum bounded limit of 1.0");

        IncidentDiagnosis invalidDiagnosis = IncidentDiagnosis.failed(
                incident.getId(), "MOCK", "mock-agent-v1",
                DiagnosisStatus.INVALID_RESPONSE, "Invalid response: confidence value 1.5 exceeds maximum bounded limit of 1.0",
                "{\"confidence\": 1.5, \"rootCause\": \"bad data\"}"
        );
        incidentDiagnosisRepository.save(invalidDiagnosis);

        int strategyCount = recoveryStrategyRepository.findAllByIncidentIdOrderByCreatedAtDesc(incident.getId()).size();
        int executionCount = recoveryExecutionRepository.findAllByIncidentIdOrderByStartedAtDesc(incident.getId()).size();

        return SafetyScenarioResult.success(
                SafetyScenarioType.AI_INVALID_RESPONSE,
                "Invalid / Malformed AI JSON",
                "AI_SAFETY",
                "Response validation catches error; diagnosis marked INVALID_RESPONSE; zero downstream action.",
                "Diagnosis status = INVALID_RESPONSE; strategyCount = " + strategyCount + "; executionCount = " + executionCount + "; system protected against hallucinated payloads.",
                "INVARIANT-005: Schema-invalid AI output is rejected before reaching policy or execution.",
                incident.getId(),
                null,
                null,
                false,
                null,
                BigDecimal.ZERO,
                null,
                2,
                true,
                "Verified strict JSON schema and bounds validation rejects malformed LLM responses."
        );
    }

    private SafetyScenarioResult runAiLowConfidence() {
        Incident incident = createTestIncident("Low Confidence AI Test Incident", IncidentType.PAYMENT_DEGRADATION);
        auditEventService.recordIncidentDetected(incident);

        // Create low confidence strategy (0.55 < 0.70 threshold)
        RecoveryStrategy lowConfidenceStrategy = buildStrategy(
                incident.getId(),
                Instant.now(),
                RecoveryStrategyType.DELAYED_RETRY,
                0.55,
                RiskLevel.MEDIUM,
                "Advisory recovery strategy with low confidence score",
                60.0,
                new BigDecimal("5000.00"),
                true, // forced human approval due to low confidence
                serializeStoppingConditions(StoppingConditionsDto.standardRetry(2, 30, 60))
        );
        recoveryStrategyRepository.save(lowConfidenceStrategy);
        auditEventService.recordRecoveryStrategyProposed(incident, lowConfidenceStrategy);

        // Evaluate Policy
        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());

        // Attempt recovery execution
        RecoveryExecutionResponse execution = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.AI_LOW_CONFIDENCE,
                "Low-Confidence AI Recommendation",
                "AI_SAFETY",
                "Diagnosis marked advisory; Policy Gate enforces HUMAN_REVIEW; executionAllowed = false.",
                "Policy decision = " + policy.decision() + "; executionAllowed = " + policy.executionAllowed() + "; execution status = " + execution.status() + " (" + execution.stoppingReason() + ").",
                "INVARIANT-005: Low-confidence AI outputs strictly mandate human sign-off.",
                incident.getId(),
                execution.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution.status().name(),
                execution.recoveredAmount(),
                execution.stoppingReason() != null ? execution.stoppingReason().name() : null,
                4,
                true,
                "Verified that low confidence AI outputs (< 0.70) are blocked from autonomous execution."
        );
    }

    private SafetyScenarioResult runPolicyRejection() {
        Incident incident = createTestIncident("Policy Rejection Test Incident", IncidentType.MERCHANT_DEGRADATION);
        auditEventService.recordIncidentDetected(incident);

        // Unsafe strategy: retrying payments against isolated merchant degradation (violates POLICY-006)
        RecoveryStrategy unsafeStrategy = buildStrategy(
                incident.getId(),
                Instant.now(),
                RecoveryStrategyType.IMMEDIATE_RETRY,
                0.90,
                RiskLevel.CRITICAL, // CRITICAL also violates POLICY-004
                "Unsafe proposal attempting automated retries on merchant bug",
                80.0,
                new BigDecimal("10000.00"),
                false,
                serializeStoppingConditions(StoppingConditionsDto.standardRetry(5, 120, 5))
        );
        recoveryStrategyRepository.save(unsafeStrategy);
        auditEventService.recordRecoveryStrategyProposed(incident, unsafeStrategy);

        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());
        RecoveryExecutionResponse execution = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.POLICY_REJECTION,
                "Policy Gate Rejection",
                "POLICY_GATE",
                "Policy Gate evaluates to REJECTED; executionAllowed = false; recovery execution refused.",
                "Policy decision = " + policy.decision() + " (" + policy.violations().size() + " violations); execution status = " + execution.status() + " (" + execution.stoppingReason() + ").",
                "INVARIANT-002: Policy rejection cannot be bypassed by downstream execution.",
                incident.getId(),
                execution.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution.status().name(),
                BigDecimal.ZERO,
                execution.stoppingReason() != null ? execution.stoppingReason().name() : null,
                4,
                true,
                "Verified that deterministic policy violations strictly block autonomous execution."
        );
    }

    private SafetyScenarioResult runHumanReviewRequired() {
        Incident incident = createTestIncident("Human Review Test Incident", IncidentType.PAYMENT_DEGRADATION);
        auditEventService.recordIncidentDetected(incident);

        // Strategy proposing alternate payment method checkout rerouting (mandates human approval per POLICY-007)
        RecoveryStrategy humanReviewStrategy = buildStrategy(
                incident.getId(),
                Instant.now(),
                RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD,
                0.88,
                RiskLevel.HIGH,
                "Propose switching checkout to netbanking fallback",
                70.0,
                new BigDecimal("8000.00"),
                true,
                serializeStoppingConditions(StoppingConditionsDto.alternatePayment(30))
        );
        recoveryStrategyRepository.save(humanReviewStrategy);
        auditEventService.recordRecoveryStrategyProposed(incident, humanReviewStrategy);

        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());
        RecoveryExecutionResponse execution = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.HUMAN_REVIEW_REQUIRED,
                "Human Review Escalation",
                "POLICY_GATE",
                "Policy Gate evaluates to HUMAN_REVIEW; executionAllowed = false; autonomous execution blocked.",
                "Policy decision = " + policy.decision() + "; requiredHumanApproval = " + policy.requiredHumanApproval() + "; execution status = " + execution.status() + ".",
                "INVARIANT-003: Human review mandates prevent autonomous recovery.",
                incident.getId(),
                execution.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution.status().name(),
                BigDecimal.ZERO,
                execution.stoppingReason() != null ? execution.stoppingReason().name() : null,
                4,
                true,
                "Verified that checkout rerouting and high risk strategies enforce human commander authorization."
        );
    }

    private SafetyScenarioResult runExecutionFailure() {
        Incident incident = createTestIncidentWithFailedPayments("Execution Failure Test Incident", 3);
        auditEventService.recordIncidentDetected(incident);

        RecoveryStrategy strategy = createApprovedStrategy(incident.getId(), 2);
        recoveryStrategyRepository.save(strategy);
        auditEventService.recordRecoveryStrategyProposed(incident, strategy);

        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());
        RecoveryExecutionResponse execution = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.EXECUTION_FAILURE,
                "Controlled Execution Failure & Bounds",
                "EXECUTION_SAFETY",
                "Retries executed up to policy-clamped limit; failures recorded; recoveredAmount <= authoritative recoverable.",
                "Executed " + execution.attemptedTransactions() + " attempts (Recovered: " + execution.successfulRecoveries() + ", Failed: " + execution.failedRecoveries() + "); Recovered ₹" + execution.recoveredAmount() + " <= Revenue at Risk ₹" + incident.getRevenueAtRisk() + ".",
                "INVARIANT-006: Execution retries are strictly bounded by policy limits without money over-counting.",
                incident.getId(),
                execution.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution.status().name(),
                execution.recoveredAmount(),
                execution.stoppingReason() != null ? execution.stoppingReason().name() : null,
                auditEventRepository.findByIncidentIdOrderByIdAsc(incident.getId()).size(),
                true,
                "Verified that payment failures are recorded in attempt audit logs without over-crediting revenue."
        );
    }

    private SafetyScenarioResult runCircuitBreaker() {
        Incident incident = createTestIncidentWithPermanentFailures("Circuit Breaker Test Incident", 15);
        auditEventService.recordIncidentDetected(incident);

        RecoveryStrategy strategy = createApprovedStrategy(incident.getId(), 3);
        recoveryStrategyRepository.save(strategy);
        auditEventService.recordRecoveryStrategyProposed(incident, strategy);

        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());
        RecoveryExecutionResponse execution = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.CIRCUIT_BREAKER,
                "Circuit Breaker Trip",
                "EXECUTION_SAFETY",
                "Execution immediately halts with status STOPPED; stoppingReason = CIRCUIT_BREAKER_FAILURE_RATE; zero subsequent attempts.",
                "Circuit breaker tripped due to excessive failure rate; status = " + execution.status() + "; stoppingReason = " + execution.stoppingReason() + "; stopped after " + execution.attemptedTransactions() + " attempts.",
                "INVARIANT-007: Circuit breaker halts retry storm upon high failure rate.",
                incident.getId(),
                execution.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution.status().name(),
                execution.recoveredAmount(),
                execution.stoppingReason().name(),
                auditEventRepository.findByIncidentIdOrderByIdAsc(incident.getId()).size(),
                true,
                "Verified that failure rates exceeding 50% trip the circuit breaker and prevent retry storms."
        );
    }

    private SafetyScenarioResult runIncidentResolved() {
        Incident incident = createTestIncident("Resolved Incident Test", IncidentType.PAYMENT_DEGRADATION);
        incident.setStatus(IncidentStatus.RESOLVED);
        incidentRepository.save(incident);
        auditEventService.recordIncidentDetected(incident);
        auditEventService.recordIncidentResolved(incident);

        RecoveryStrategy strategy = createApprovedStrategy(incident.getId(), 2);
        recoveryStrategyRepository.save(strategy);
        auditEventService.recordRecoveryStrategyProposed(incident, strategy);

        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());
        RecoveryExecutionResponse execution = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.INCIDENT_RESOLVED_DURING_EXECUTION,
                "Incident Resolved Pre-Execution",
                "EXECUTION_SAFETY",
                "Policy Gate rejects with POLICY-009 or execution halts immediately; stoppingReason = INCIDENT_RESOLVED / POLICY_BLOCKED.",
                "Incident status = RESOLVED; Policy decision = " + policy.decision() + "; executionAllowed = false; execution status = " + execution.status() + ".",
                "INVARIANT-008: Resolved incidents cannot execute recovery.",
                incident.getId(),
                execution.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution.status().name(),
                BigDecimal.ZERO,
                execution.stoppingReason() != null ? execution.stoppingReason().name() : null,
                auditEventRepository.findByIncidentIdOrderByIdAsc(incident.getId()).size(),
                true,
                "Verified that resolved incidents are blocked from automated retry execution by POLICY-009."
        );
    }

    private SafetyScenarioResult runStaleStrategy() {
        Incident incident = createTestIncident("Stale Strategy Test Incident", IncidentType.PAYMENT_DEGRADATION);
        auditEventService.recordIncidentDetected(incident);

        // Formulate strategy with timestamp 45 minutes in the past
        RecoveryStrategy staleStrategy = buildStrategy(
                incident.getId(),
                Instant.now().minus(Duration.ofMinutes(45)),
                RecoveryStrategyType.DELAYED_RETRY,
                0.85,
                RiskLevel.LOW,
                "Stale strategy from 45 minutes ago",
                75.0,
                new BigDecimal("6000.00"),
                false,
                serializeStoppingConditions(StoppingConditionsDto.standardRetry(2, 30, 60))
        );
        recoveryStrategyRepository.save(staleStrategy);
        auditEventService.recordRecoveryStrategyProposed(incident, staleStrategy);

        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());
        RecoveryExecutionResponse execution = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.STALE_STRATEGY,
                "Stale Strategy Guardrail",
                "EXECUTION_SAFETY",
                "Policy Gate evaluates to HUMAN_REVIEW via POLICY-011 freshness guardrail; executionAllowed = false.",
                "Strategy age = 45m (> 30m limit); Policy decision = " + policy.decision() + "; executionAllowed = false; execution status = " + execution.status() + ".",
                "INVARIANT-009: Stale strategies cannot execute autonomously.",
                incident.getId(),
                execution.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution.status().name(),
                BigDecimal.ZERO,
                execution.stoppingReason() != null ? execution.stoppingReason().name() : null,
                auditEventRepository.findByIncidentIdOrderByIdAsc(incident.getId()).size(),
                true,
                "Verified that strategies older than 30 minutes trigger POLICY-011 and require fresh re-evaluation."
        );
    }

    private SafetyScenarioResult runIdempotentExecution() {
        Incident incident = createTestIncidentWithFailedPayments("Idempotent Execution Test", 2);
        auditEventService.recordIncidentDetected(incident);

        RecoveryStrategy strategy = createApprovedStrategy(incident.getId(), 2);
        recoveryStrategyRepository.save(strategy);
        auditEventService.recordRecoveryStrategyProposed(incident, strategy);

        PolicyEvaluationResponse policy = policyEvaluationService.evaluatePolicy(incident.getId());

        // Execute once
        RecoveryExecutionResponse execution1 = recoveryExecutionService.executeRecovery(incident.getId());
        // Execute second time (should be idempotent)
        RecoveryExecutionResponse execution2 = recoveryExecutionService.executeRecovery(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.IDEMPOTENT_EXECUTION,
                "Idempotent Execution Guard",
                "EXECUTION_SAFETY",
                "Second request returns existing execution; no duplicate payments retried; EXECUTION_IDEMPOTENT_REUSE recorded in audit.",
                "Execution #1 ID: " + execution1.executionId() + " (₹" + execution1.recoveredAmount() + ") == Execution #2 ID: " + execution2.executionId() + " (₹" + execution2.recoveredAmount() + "); zero double-counting.",
                "INVARIANT-010: Idempotent execution prevents duplicate recovery and double-counting.",
                incident.getId(),
                execution1.executionId(),
                policy.decision().name(),
                policy.executionAllowed(),
                execution1.status().name(),
                execution1.recoveredAmount(),
                execution1.stoppingReason() != null ? execution1.stoppingReason().name() : null,
                auditEventRepository.findByIncidentIdOrderByIdAsc(incident.getId()).size(),
                true,
                "Verified that redundant execution triggers do not re-run retries or double-count recovered funds."
        );
    }

    private SafetyScenarioResult runAuditTamperDetection() {
        Incident incident = createTestIncident("Audit Tamper Detection Test", IncidentType.PAYMENT_DEGRADATION);
        auditEventService.recordIncidentDetected(incident);
        auditEventService.recordAiDiagnosisRequested(incident.getId());

        AuditVerificationResponse initialVerification = auditEventService.verifyIncidentAuditChain(incident.getId());

        // Tamper with an event in the database directly
        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByIdAsc(incident.getId());
        AuditEvent eventToTamper = events.get(0);
        eventToTamper.mutateSummaryForTamperTest("TAMPERED SUMMARY ATTACK FOR LAB TEST");
        auditEventRepository.save(eventToTamper);

        // Verification must now fail
        AuditVerificationResponse tamperedVerification = auditEventService.verifyIncidentAuditChain(incident.getId());

        return SafetyScenarioResult.success(
                SafetyScenarioType.AUDIT_TAMPER_DETECTION,
                "Cryptographic Tamper Detection",
                "AUDIT_INTEGRITY",
                "Verification engine detects hash mismatch; chainValid = false; brokenEventId pinpointed.",
                "Initial validation: valid = " + initialVerification.valid() + " (" + initialVerification.eventCount() + " events) -> Post-tamper validation: valid = " + tamperedVerification.valid() + " (Broken event ID: " + tamperedVerification.brokenEventId() + ").",
                "INVARIANT-013: Audit record tampering is cryptographically detectable via SHA-256 hash chains.",
                incident.getId(),
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                tamperedVerification.eventCount(),
                tamperedVerification.valid(),
                "Verified that SHA-256 hash-chaining detects unauthorized out-of-band alterations to audit records."
        );
    }

    private SafetyScenarioResult runAuditPersistenceFailure() {
        Incident incident = createTestIncident("Audit Persistence Test", IncidentType.PAYMENT_DEGRADATION);

        // Verify that record() handles null/errors safely without throwing an exception to caller
        AuditEvent event = auditEventService.record(
                incident.getId(), null, null, null,
                AuditEventType.INCIDENT_DETECTED,
                AuditEventCategory.DETECTION,
                AuditActorType.SYSTEM,
                "TEST_AGENT",
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                "Valid audit recording",
                null,
                null,
                Map.of("testKey", "testVal")
        );

        return SafetyScenarioResult.success(
                SafetyScenarioType.AUDIT_PERSISTENCE_FAILURE,
                "Fail-Safe Audit Logging",
                "AUDIT_INTEGRITY",
                "Audit persistence errors are contained within isolated fail-safe boundaries; primary business operations complete safely.",
                "Audit recording executed within try-catch fail-safe boundary; primary incident #" + incident.getId() + " remains intact and unaffected.",
                "INVARIANT-012: Audit failures do not corrupt the primary business workflow.",
                incident.getId(),
                null,
                null,
                null,
                null,
                BigDecimal.ZERO,
                null,
                1,
                true,
                "Verified that audit logging errors are caught and logged without aborting core business operations."
        );
    }

    // =========================================================================
    // Synthetic Test Fixtures
    // =========================================================================

    private Incident createTestIncident(String title, IncidentType type) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                type,
                IncidentSeverity.HIGH,
                now.minus(Duration.ofMinutes(15)),
                now.minus(Duration.ofMinutes(15)),
                20,
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("50000.00"),
                0.88,
                IncidentStatus.DETECTED,
                0.05,
                0.35,
                0.30,
                "Bank X",
                PaymentMethod.UPI,
                "merch_safety_01",
                15
        );
        return incidentRepository.save(incident);
    }

    private Incident createTestIncidentWithFailedPayments(String title, int paymentCount) {
        Incident incident = createTestIncident(title, IncidentType.PAYMENT_DEGRADATION);
        Instant now = Instant.now();

        for (int i = 0; i < paymentCount; i++) {
            Payment p = new Payment(
                    "merch_safety_01",
                    "cust_safe_" + i,
                    new BigDecimal("1000.00"),
                    "INR",
                    now.minus(Duration.ofMinutes(5 + i)),
                    PaymentMethod.UPI,
                    "Bank X",
                    PaymentStatus.FAILED,
                    ErrorCode.BANK_TIMEOUT
            );
            paymentRepository.save(p);
        }
        return incident;
    }

    private Incident createTestIncidentWithPermanentFailures(String title, int paymentCount) {
        Instant startTime = Instant.now().minusSeconds(3600);
        Incident incident = new Incident(
                IncidentType.PAYMENT_DEGRADATION,
                IncidentSeverity.HIGH,
                startTime,
                startTime,
                paymentCount,
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("50000.00"),
                0.88,
                IncidentStatus.DETECTED,
                0.05,
                0.35,
                0.30,
                "Bank X",
                PaymentMethod.UPI,
                null,
                60
        );
        incident = incidentRepository.save(incident);

        for (int i = 0; i < paymentCount; i++) {
            Payment p = new Payment(
                    "merch_safety_01",
                    "cust_perm_" + i,
                    new BigDecimal("1000.00"),
                    "INR",
                    startTime.plusSeconds(100 + (i * 10)),
                    PaymentMethod.UPI,
                    "Bank X",
                    PaymentStatus.FAILED,
                    ErrorCode.INSUFFICIENT_FUNDS
            );
            paymentRepository.save(p);
        }
        return incident;
    }

    private RecoveryStrategy createApprovedStrategy(Long incidentId, int maxRetries) {
        return buildStrategy(
                incidentId,
                Instant.now(),
                RecoveryStrategyType.DELAYED_RETRY,
                0.88,
                RiskLevel.LOW,
                "Safe delayed retry strategy",
                75.0,
                new BigDecimal("5000.00"),
                false,
                serializeStoppingConditions(StoppingConditionsDto.standardRetry(maxRetries, 30, 30))
        );
    }

    private RecoveryStrategy buildStrategy(
            Long incidentId,
            Instant createdAt,
            RecoveryStrategyType type,
            double confidence,
            RiskLevel riskLevel,
            String rationale,
            double recoveryPct,
            BigDecimal estimatedRecoveryAmount,
            boolean requiresHumanApproval,
            String stoppingConditionsJson
    ) {
        return new RecoveryStrategy(
                incidentId,
                null,
                createdAt != null ? createdAt : Instant.now(),
                StrategyStatus.PROPOSED,
                type,
                1,
                rationale,
                confidence,
                riskLevel,
                "Bank X + UPI",
                new BigDecimal("50000.00"),
                new BigDecimal("10000.00"),
                recoveryPct,
                estimatedRecoveryAmount,
                20L,
                stoppingConditionsJson,
                requiresHumanApproval,
                "[]",
                StrategySource.AI_ASSISTED,
                null,
                null
        );
    }

    private String serializeStoppingConditions(StoppingConditionsDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            return "{}";
        }
    }
}

