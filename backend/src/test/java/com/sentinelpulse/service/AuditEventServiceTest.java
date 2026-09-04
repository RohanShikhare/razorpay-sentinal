package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.ControlledAction;
import com.sentinelpulse.ai.DiagnosisStatus;
import com.sentinelpulse.ai.InvestigationStatus;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.audit.AuditActorType;
import com.sentinelpulse.audit.AuditEventCategory;
import com.sentinelpulse.audit.AuditEventType;
import com.sentinelpulse.audit.AuditSeverity;
import com.sentinelpulse.audit.AuditStatus;
import com.sentinelpulse.dto.AuditTimelineResponse;
import com.sentinelpulse.dto.AuditVerificationResponse;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.entity.AuditEvent;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentDiagnosis;
import com.sentinelpulse.entity.IncidentInvestigation;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PolicyEvaluation;
import com.sentinelpulse.entity.RecoveryExecution;
import com.sentinelpulse.entity.RecoveryExecutionAttempt;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.RecoveryOutcome;
import com.sentinelpulse.recovery.StoppingReason;
import com.sentinelpulse.repository.AuditEventRepository;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AuditEventServiceTest {

    @Autowired
    private AuditEventService auditEventService;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentDiagnosisRepository incidentDiagnosisRepository;

    @Autowired
    private IncidentInvestigationRepository incidentInvestigationRepository;

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
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        auditEventRepository.deleteAll();
        recoveryExecutionAttemptRepository.deleteAll();
        recoveryExecutionRepository.deleteAll();
        policyEvaluationRepository.deleteAll();
        recoveryStrategyRepository.deleteAll();
        incidentInvestigationRepository.deleteAll();
        incidentDiagnosisRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private Incident createIncident() {
        Instant now = Instant.now();
        Incident incident = new Incident(
                IncidentType.PAYMENT_DEGRADATION,
                IncidentSeverity.HIGH,
                now,
                now.minusSeconds(3600),
                100,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(60_000),
                BigDecimal.valueOf(50_000),
                88.0,
                IncidentStatus.DETECTED,
                3.5,
                78.5,
                75.0,
                "HDFC",
                PaymentMethod.UPI,
                null,
                60
        );
        return incidentRepository.save(incident);
    }

    @Test
    @DisplayName("1. Incident detection event is recorded with metadata")
    void testRecordIncidentDetected() {
        Incident incident = createIncident();
        auditEventService.recordIncidentDetected(incident);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(1);

        AuditEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo(AuditEventType.INCIDENT_DETECTED);
        assertThat(event.getEventCategory()).isEqualTo(AuditEventCategory.DETECTION);
        assertThat(event.getSummary()).contains("PAYMENT_DEGRADATION");
        assertThat(event.getMetadataJson()).contains("HDFC");
        assertThat(event.getPreviousEventHash()).isNull();
        assertThat(event.getEventHash()).isNotBlank();
    }

    @Test
    @DisplayName("2. Financial impact event is recorded with authoritative values")
    void testRecordFinancialImpactCalculated() {
        Incident incident = createIncident();
        FinancialImpactResponse impact = new FinancialImpactResponse(
                incident.getId(),
                100L,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(60_000),
                BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(35_000),
                BigDecimal.valueOf(15_000),
                BigDecimal.valueOf(2_000),
                BigDecimal.valueOf(10_000),
                70.0
        );

        auditEventService.recordFinancialImpactCalculated(incident, impact);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.FINANCIAL_IMPACT_CALCULATED);
        assertThat(events.get(0).getEventCategory()).isEqualTo(AuditEventCategory.FINANCIAL);
        assertThat(events.get(0).getSummary()).contains("₹50000");
    }

    @Test
    @DisplayName("3. AI diagnosis event completed is recorded with AI actor model")
    void testRecordAiDiagnosisCompleted() {
        Incident incident = createIncident();
        IncidentDiagnosis diagnosis = new IncidentDiagnosis(
                incident.getId(), Instant.now(), "MOCK", "mock-v1",
                DiagnosisStatus.COMPLETED, IncidentType.PAYMENT_DEGRADATION,
                "Bank Timeout Surge", 0.92, "High timeout rates from issuer rail",
                "[\"e1\"]", "HDFC UPI", ControlledAction.DELAYED_RETRY,
                65.0, RiskLevel.MEDIUM, false, "{}", null
        );
        diagnosis = incidentDiagnosisRepository.save(diagnosis);

        auditEventService.recordAiDiagnosisCompleted(incident, diagnosis);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(1);
        AuditEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo(AuditEventType.AI_DIAGNOSIS_COMPLETED);
        assertThat(event.getActorType()).isEqualTo(AuditActorType.AI);
        assertThat(event.getActorId()).isEqualTo("MOCK:mock-v1");
        assertThat(event.getSummary()).contains("Bank Timeout Surge");
    }

    @Test
    @DisplayName("4. Investigation completed event is recorded")
    void testRecordInvestigationCompleted() {
        Incident incident = createIncident();
        IncidentInvestigation investigation = new IncidentInvestigation(
                incident.getId(), Instant.now(), Instant.now(),
                InvestigationStatus.COMPLETED, "MOCK", "investigation-v1",
                IncidentType.PAYMENT_DEGRADATION, "Deep analysis completed",
                "HDFC UPI Switch Degradation", 0.89, "HDFC UPI",
                "Switch latency degradation observed", "Low", false,
                "[]", "[]", "[]", "{}", "Success"
        );
        investigation = incidentInvestigationRepository.save(investigation);

        auditEventService.recordInvestigationCompleted(incident, investigation);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.INVESTIGATION_COMPLETED);
        assertThat(events.get(0).getSummary()).contains("HDFC UPI Switch Degradation");
    }

    @Test
    @DisplayName("5. Recovery strategy proposed event is recorded")
    void testRecordRecoveryStrategyProposed() {
        Incident incident = createIncident();
        RecoveryStrategy strategy = new RecoveryStrategy(
                incident.getId(), null, Instant.now(), StrategyStatus.PROPOSED,
                RecoveryStrategyType.DELAYED_RETRY, 1, "Optimal backoff retry window",
                0.85, RiskLevel.MEDIUM, "HDFC UPI",
                BigDecimal.valueOf(50_000), BigDecimal.valueOf(35_000),
                70.0, BigDecimal.valueOf(24_500), 40L,
                "{}", false, "[]", StrategySource.AI_ASSISTED, "{}", "OK"
        );
        strategy = recoveryStrategyRepository.save(strategy);

        auditEventService.recordRecoveryStrategyProposed(incident, strategy);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.RECOVERY_STRATEGY_PROPOSED);
        assertThat(events.get(0).getEventCategory()).isEqualTo(AuditEventCategory.STRATEGY);
        assertThat(events.get(0).getStrategyId()).isEqualTo(strategy.getId());
    }

    @Test
    @DisplayName("6. Policy approval event recorded")
    void testRecordPolicyApproval() {
        Incident incident = createIncident();
        PolicyEvaluation policy = new PolicyEvaluation(
                incident.getId(), 10L, Instant.now(), "POLICY_ENGINE_V1",
                PolicyDecision.APPROVED, true, true, false,
                RiskLevel.LOW, 95.0, "Strategy within safe operational bounds",
                null, "PROCEED_TO_EXECUTION", "[]", "[]", "[]", "[]"
        );
        policy = policyEvaluationRepository.save(policy);

        auditEventService.recordPolicyEvaluation(incident, policy);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.POLICY_APPROVED);
        assertThat(events.get(0).getActorType()).isEqualTo(AuditActorType.POLICY_ENGINE);
        assertThat(events.get(0).getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    @DisplayName("7. Policy rejection event recorded")
    void testRecordPolicyRejection() {
        Incident incident = createIncident();
        PolicyEvaluation policy = new PolicyEvaluation(
                incident.getId(), 10L, Instant.now(), "POLICY_ENGINE_V1",
                PolicyDecision.REJECTED, false, false, true,
                RiskLevel.CRITICAL, 20.0, "Safety violation",
                "Unbounded retries prohibited", "ABORT", "[]", "[]", "[\"VIOLATION\"]", "[]"
        );
        policy = policyEvaluationRepository.save(policy);

        auditEventService.recordPolicyEvaluation(incident, policy);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.POLICY_REJECTED);
        assertThat(events.get(0).getActorType()).isEqualTo(AuditActorType.POLICY_ENGINE);
        assertThat(events.get(0).getStatus()).isEqualTo(AuditStatus.BLOCKED);
    }

    @Test
    @DisplayName("8. Recovery execution started and completed events recorded")
    void testRecordRecoveryExecutionLifecycle() {
        Incident incident = createIncident();
        RecoveryExecution execution = new RecoveryExecution(
                incident.getId(), 10L, 20L, Instant.now(), Instant.now(),
                ExecutionStatus.COMPLETED, RecoveryStrategyType.DELAYED_RETRY,
                25L, 25L, 20L, 5L, 0L,
                BigDecimal.valueOf(18_000), BigDecimal.valueOf(22_000),
                BigDecimal.valueOf(2_000), BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(20_000), 90.0, 80.0, 90.0,
                StoppingReason.COMPLETED_ALL_ELIGIBLE, "Success",
                true, 2, 30, 30, "SIM_V1", "{}"
        );
        execution = recoveryExecutionRepository.save(execution);

        auditEventService.recordRecoveryExecutionStarted(execution);
        auditEventService.recordRecoveryExecutionCompleted(execution);

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.RECOVERY_EXECUTION_STARTED);
        assertThat(events.get(1).getEventType()).isEqualTo(AuditEventType.RECOVERY_COMPLETED);
    }

    @Test
    @DisplayName("9. Recovery attempt event recorded")
    void testRecordRecoveryAttempt() {
        Incident incident = createIncident();
        RecoveryExecution execution = new RecoveryExecution(
                incident.getId(), 10L, 20L, Instant.now(), null,
                ExecutionStatus.RUNNING, RecoveryStrategyType.DELAYED_RETRY,
                10L, 1L, 1L, 0L, 0L,
                BigDecimal.valueOf(1000), BigDecimal.valueOf(1000),
                BigDecimal.ZERO, BigDecimal.valueOf(50_000),
                BigDecimal.valueOf(10_000), 90.0, 10.0, 10.0,
                StoppingReason.COMPLETED_ALL_ELIGIBLE, "In progress",
                true, 2, 30, 30, "SIM_V1", "{}"
        );
        execution = recoveryExecutionRepository.save(execution);

        RecoveryExecutionAttempt attempt = new RecoveryExecutionAttempt(
                execution.getId(), 5001L, 1, Instant.now(),
                RecoveryOutcome.RECOVERED, BigDecimal.valueOf(1000),
                null, RecoveryStrategyType.DELAYED_RETRY, 0
        );

        auditEventService.recordRecoveryAttempt(execution, attempt);

        List<AuditEvent> events = auditEventRepository.findByExecutionIdOrderByTimestampAsc(execution.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(AuditEventType.RECOVERY_SUCCEEDED);
        assertThat(events.get(0).getActorType()).isEqualTo(AuditActorType.RECOVERY_ENGINE);
    }

    @Test
    @DisplayName("10. SHA-256 hash chaining links events in incident timeline")
    void testHashChaining() {
        Incident incident = createIncident();

        auditEventService.recordIncidentDetected(incident);
        auditEventService.recordAiDiagnosisRequested(incident.getId());

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByTimestampAsc(incident.getId());
        assertThat(events).hasSize(2);

        AuditEvent first = events.get(0);
        AuditEvent second = events.get(1);

        assertThat(first.getPreviousEventHash()).isNull();
        assertThat(first.getEventHash()).isNotBlank();

        assertThat(second.getPreviousEventHash()).isEqualTo(first.getEventHash());
        assertThat(second.getEventHash()).isNotBlank();
        assertThat(second.getEventHash()).isNotEqualTo(first.getEventHash());
    }

    @Test
    @DisplayName("11. Valid hash chain passes cryptographic verification")
    void testVerifyValidChain() {
        Incident incident = createIncident();

        auditEventService.recordIncidentDetected(incident);
        auditEventService.recordAiDiagnosisRequested(incident.getId());

        AuditVerificationResponse verification = auditEventService.verifyIncidentAuditChain(incident.getId());
        assertThat(verification.valid()).isTrue();
        assertThat(verification.eventCount()).isEqualTo(2);
        assertThat(verification.brokenEventId()).isNull();
    }

    @Test
    @DisplayName("12. Tampered event triggers cryptographic verification failure")
    void testVerifyTamperedChain() {
        Incident incident = createIncident();

        auditEventService.recordIncidentDetected(incident);
        auditEventService.recordAiDiagnosisRequested(incident.getId());

        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByIdAsc(incident.getId());
        AuditEvent eventToTamper = events.get(0);

        // Tamper with the summary directly in the database without changing the recorded eventHash or ID
        eventToTamper.mutateSummaryForTamperTest("TAMPERED SUMMARY ATTACK");
        auditEventRepository.save(eventToTamper);

        AuditVerificationResponse verification = auditEventService.verifyIncidentAuditChain(incident.getId());
        assertThat(verification.valid()).isFalse();
        assertThat(verification.brokenEventId()).isNotNull();
        assertThat(verification.message()).contains("mismatch/tampering");
    }

    @Test
    @DisplayName("13. Incident timeline returns chronological events with decision chain summary")
    void testGetIncidentTimeline() {
        Incident incident = createIncident();

        auditEventService.recordIncidentDetected(incident);
        auditEventService.recordAiDiagnosisRequested(incident.getId());

        AuditTimelineResponse timeline = auditEventService.getIncidentTimeline(incident.getId());
        assertThat(timeline.incidentId()).isEqualTo(incident.getId());
        assertThat(timeline.totalEvents()).isEqualTo(2);
        assertThat(timeline.chainValid()).isTrue();
        assertThat(timeline.events()).hasSize(2);
        assertThat(timeline.decisionChain()).isNotNull();
        assertThat(timeline.decisionChain().detected()).isTrue();
    }

    @Test
    @DisplayName("14. Execution timeline filters events strictly by executionId")
    void testGetExecutionTimeline() {
        Incident incident = createIncident();

        RecoveryExecution execution1 = new RecoveryExecution(
                incident.getId(), 1L, 1L, Instant.now(), Instant.now(),
                ExecutionStatus.COMPLETED, RecoveryStrategyType.DELAYED_RETRY,
                5L, 5L, 5L, 0L, 0L,
                BigDecimal.valueOf(5000), BigDecimal.valueOf(5000),
                BigDecimal.ZERO, BigDecimal.valueOf(50000),
                BigDecimal.valueOf(5000), 100.0, 100.0, 100.0,
                StoppingReason.COMPLETED_ALL_ELIGIBLE, "OK",
                true, 2, 30, 30, "SIM_V1", "{}"
        );
        execution1 = recoveryExecutionRepository.save(execution1);

        RecoveryExecution execution2 = new RecoveryExecution(
                incident.getId(), 2L, 2L, Instant.now(), Instant.now(),
                ExecutionStatus.COMPLETED, RecoveryStrategyType.DELAYED_RETRY,
                5L, 5L, 5L, 0L, 0L,
                BigDecimal.valueOf(3000), BigDecimal.valueOf(3000),
                BigDecimal.ZERO, BigDecimal.valueOf(50000),
                BigDecimal.valueOf(3000), 100.0, 100.0, 100.0,
                StoppingReason.COMPLETED_ALL_ELIGIBLE, "OK",
                true, 2, 30, 30, "SIM_V1", "{}"
        );
        execution2 = recoveryExecutionRepository.save(execution2);

        auditEventService.recordRecoveryExecutionStarted(execution1);
        auditEventService.recordRecoveryExecutionCompleted(execution1);
        auditEventService.recordRecoveryExecutionStarted(execution2);

        final Long exec1Id = execution1.getId();
        AuditTimelineResponse exec1Timeline = auditEventService.getExecutionTimeline(exec1Id);
        assertThat(exec1Timeline.totalEvents()).isEqualTo(2);
        assertThat(exec1Timeline.events()).allMatch(e -> exec1Id.equals(e.executionId()));
    }
}
