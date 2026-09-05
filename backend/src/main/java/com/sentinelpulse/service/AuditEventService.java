package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.audit.AuditActorType;
import com.sentinelpulse.audit.AuditEventCategory;
import com.sentinelpulse.audit.AuditEventType;
import com.sentinelpulse.audit.AuditSeverity;
import com.sentinelpulse.audit.AuditStatus;
import com.sentinelpulse.dto.AuditEventDto;
import com.sentinelpulse.dto.AuditTimelineResponse;
import com.sentinelpulse.dto.AuditVerificationResponse;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.entity.AuditEvent;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentDiagnosis;
import com.sentinelpulse.entity.IncidentInvestigation;
import com.sentinelpulse.entity.PolicyEvaluation;
import com.sentinelpulse.entity.RecoveryExecution;
import com.sentinelpulse.entity.RecoveryExecutionAttempt;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.StoppingReason;
import com.sentinelpulse.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing the immutable, tamper-evident Sentinel Audit Trail.
 *
 * <p>Architectural Invariants:
 * <ul>
 *   <li>AI proposes recovery actions.</li>
 *   <li>Policy decides whether actions are permitted.</li>
 *   <li>Execution obeys policy.</li>
 *   <li>Audit records what occurred without making business decisions.</li>
 *   <li>Zero AI calls and zero business logic mutations.</li>
 *   <li>SHA-256 cryptographic hash-chaining per incident.</li>
 * </ul>
 */
@Service
public class AuditEventService {

    private static final Logger log = LoggerFactory.getLogger(AuditEventService.class);

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    public AuditEventService(AuditEventRepository auditEventRepository, ObjectMapper objectMapper) {
        this.auditEventRepository = auditEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Records a generic audit event with deterministic hash chaining.
     * Uses REQUIRES_NEW propagation where appropriate to ensure audit records are isolated.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public synchronized AuditEvent record(Long incidentId, Long executionId, Long strategyId,
                                          Long policyEvaluationId, AuditEventType eventType,
                                          AuditEventCategory category, AuditActorType actorType,
                                          String actorId, AuditSeverity severity,
                                          AuditStatus status, String summary, String reason,
                                          Object evidenceObj, Object metadataObj) {
        try {
            Instant now = Instant.now();
            String eventId = "AUD-" + UUID.randomUUID();

            String evidenceJson = serializeToJson(evidenceObj);
            String metadataJson = serializeToJson(metadataObj);

            // Retrieve previous event hash for this incident
            String previousEventHash = null;
            if (incidentId != null) {
                Optional<AuditEvent> latest = auditEventRepository.findTopByIncidentIdOrderByIdDesc(incidentId);
                if (latest.isPresent()) {
                    previousEventHash = latest.get().getEventHash();
                }
            }

            // Compute SHA-256 event hash
            String eventHash = calculateEventHash(
                    previousEventHash, eventId, incidentId, eventType, now, actorType, summary, metadataJson
            );

            AuditEvent event = new AuditEvent(
                    eventId, incidentId, executionId, strategyId, policyEvaluationId,
                    eventType, category, actorType, actorId != null ? actorId : "SYSTEM",
                    severity, status, summary, reason, evidenceJson, metadataJson,
                    now, previousEventHash, eventHash
            );

            AuditEvent saved = auditEventRepository.save(event);
            log.info("Recorded audit event [{}] for incident #{}: {}", eventType, incidentId, summary);
            return saved;
        } catch (Exception ex) {
            log.error("Failed to record audit event [{}] for incident #{}: {}", eventType, incidentId, ex.getMessage(), ex);
            // Non-blocking: Do not crash core payment or incident workflows
            return null;
        }
    }

    // =========================================================================
    // Lifecycle Hook Helpers
    // =========================================================================

    public void recordIncidentDetected(Incident incident) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", incident.getType() != null ? incident.getType().name() : null);
        metadata.put("severity", incident.getSeverity() != null ? incident.getSeverity().name() : null);
        metadata.put("status", incident.getStatus() != null ? incident.getStatus().name() : null);
        metadata.put("baselineFailureRate", incident.getBaselineFailureRate());
        metadata.put("currentFailureRate", incident.getCurrentFailureRate());
        metadata.put("affectedTransactions", incident.getAffectedTransactions());
        metadata.put("revenueAtRisk", incident.getRevenueAtRisk());
        metadata.put("targetBank", incident.getTargetBank());
        metadata.put("targetPaymentMethod", incident.getTargetPaymentMethod() != null ? incident.getTargetPaymentMethod().name() : null);

        record(
                incident.getId(), null, null, null,
                AuditEventType.INCIDENT_DETECTED,
                AuditEventCategory.DETECTION,
                AuditActorType.SYSTEM,
                "MONITORING_ENGINE_V1",
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("Incident #%d detected: %s (Failure rate spiked to %.2f%%, Revenue at risk: ₹%s)",
                        incident.getId(), incident.getType(), incident.getCurrentFailureRate(), incident.getRevenueAtRisk()),
                null,
                null,
                metadata
        );
    }

    public void recordIncidentResolved(Incident incident) {
        Map<String, Object> metadata = Map.of(
                "status", incident.getStatus() != null ? incident.getStatus().name() : "RESOLVED",
                "resolvedAt", Instant.now().toString()
        );

        record(
                incident.getId(), null, null, null,
                AuditEventType.INCIDENT_RESOLVED,
                AuditEventCategory.DETECTION,
                AuditActorType.HUMAN_OPERATOR,
                "OPERATOR",
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("Incident #%d marked as RESOLVED", incident.getId()),
                "Incident condition cleared",
                null,
                metadata
        );
    }

    public void recordFinancialImpactCalculated(Incident incident, FinancialImpactResponse impact) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("revenueAtRisk", impact.revenueAtRisk());
        metadata.put("estimatedRecoverableRevenue", impact.estimatedRecoverableRevenue());
        metadata.put("nonRecoverableRevenue", impact.nonRecoverableRevenue());
        metadata.put("recoverablePercentage", impact.recoverablePercentage());
        metadata.put("affectedTransactions", impact.affectedTransactions());

        record(
                incident.getId(), null, null, null,
                AuditEventType.FINANCIAL_IMPACT_CALCULATED,
                AuditEventCategory.FINANCIAL,
                AuditActorType.SYSTEM,
                "FINANCIAL_ENGINE_V1",
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("Authoritative financial impact computed: Revenue at risk ₹%s, Recoverable ₹%s (%d affected transactions)",
                        impact.revenueAtRisk(), impact.estimatedRecoverableRevenue(), impact.affectedTransactions()),
                null,
                null,
                metadata
        );
    }

    public void recordAiDiagnosisRequested(Long incidentId) {
        record(
                incidentId, null, null, null,
                AuditEventType.AI_DIAGNOSIS_REQUESTED,
                AuditEventCategory.DIAGNOSIS,
                AuditActorType.SYSTEM,
                "AI_DIAGNOSTIC_ORCHESTRATOR",
                AuditSeverity.INFO,
                AuditStatus.INFO,
                String.format("AI root-cause diagnosis requested for incident #%d", incidentId),
                null,
                null,
                Map.of("incidentId", incidentId)
        );
    }

    public void recordAiDiagnosisCompleted(Incident incident, IncidentDiagnosis diagnosis) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("diagnosisId", diagnosis.getId());
        metadata.put("aiProvider", diagnosis.getProvider());
        metadata.put("aiModel", diagnosis.getModel());
        metadata.put("rootCause", diagnosis.getRootCause());
        metadata.put("confidence", diagnosis.getConfidence());
        metadata.put("riskLevel", diagnosis.getRiskLevel() != null ? diagnosis.getRiskLevel().name() : null);
        metadata.put("recommendedAction", diagnosis.getRecommendedAction() != null ? diagnosis.getRecommendedAction().name() : null);
        metadata.put("requiresHumanApproval", diagnosis.getRequiresHumanApproval());

        record(
                incident.getId(), null, null, null,
                AuditEventType.AI_DIAGNOSIS_COMPLETED,
                AuditEventCategory.DIAGNOSIS,
                AuditActorType.AI,
                diagnosis.getProvider() + ":" + diagnosis.getModel(),
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("AI Diagnosis completed: %s (Confidence: %.0f%%, Risk: %s)",
                        diagnosis.getRootCause(), (diagnosis.getConfidence() != null ? diagnosis.getConfidence() * 100 : 0.0), diagnosis.getRiskLevel()),
                diagnosis.getReasoning(),
                diagnosis.getEvidence(),
                metadata
        );
    }

    public void recordAiDiagnosisFailed(Long incidentId, String errorReason) {
        record(
                incidentId, null, null, null,
                AuditEventType.AI_DIAGNOSIS_FAILED,
                AuditEventCategory.DIAGNOSIS,
                AuditActorType.AI,
                "AI_DIAGNOSTIC_ORCHESTRATOR",
                AuditSeverity.WARN,
                AuditStatus.FAILURE,
                String.format("AI diagnosis failed for incident #%d: %s", incidentId, errorReason),
                errorReason,
                null,
                Map.of("error", errorReason != null ? errorReason : "unknown")
        );
    }

    public void recordInvestigationRequested(Long incidentId) {
        record(
                incidentId, null, null, null,
                AuditEventType.INVESTIGATION_REQUESTED,
                AuditEventCategory.INVESTIGATION,
                AuditActorType.SYSTEM,
                "INVESTIGATION_ORCHESTRATOR",
                AuditSeverity.INFO,
                AuditStatus.INFO,
                String.format("Deep root-cause hypothesis investigation requested for incident #%d", incidentId),
                null,
                null,
                Map.of("incidentId", incidentId)
        );
    }

    public void recordInvestigationCompleted(Incident incident, IncidentInvestigation investigation) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("investigationId", investigation.getId());
        metadata.put("aiProvider", investigation.getProvider());
        metadata.put("aiModel", investigation.getModel());
        metadata.put("strongestRootCause", investigation.getStrongestRootCause());
        metadata.put("confidence", investigation.getOverallConfidence());
        metadata.put("status", investigation.getStatus() != null ? investigation.getStatus().name() : null);

        record(
                incident.getId(), null, null, null,
                AuditEventType.INVESTIGATION_COMPLETED,
                AuditEventCategory.INVESTIGATION,
                AuditActorType.AI,
                investigation.getProvider() + ":" + investigation.getModel(),
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("Root-cause investigation evaluated hypotheses. Strongest: %s (Confidence: %.0f%%)",
                        investigation.getStrongestRootCause(),
                        (investigation.getOverallConfidence() != null ? investigation.getOverallConfidence() * 100 : 0.0)),
                investigation.getInvestigationReasoning(),
                investigation.getSupportingEvidenceJson(),
                metadata
        );
    }

    public void recordInvestigationFailed(Long incidentId, String errorReason) {
        record(
                incidentId, null, null, null,
                AuditEventType.INVESTIGATION_FAILED,
                AuditEventCategory.INVESTIGATION,
                AuditActorType.AI,
                "INVESTIGATION_ORCHESTRATOR",
                AuditSeverity.WARN,
                AuditStatus.FAILURE,
                String.format("Root-cause investigation failed for incident #%d: %s", incidentId, errorReason),
                errorReason,
                null,
                Map.of("error", errorReason != null ? errorReason : "unknown")
        );
    }

    public void recordRecoveryStrategyProposed(Incident incident, RecoveryStrategy strategy) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("strategyId", strategy.getId());
        metadata.put("strategyType", strategy.getSelectedStrategy() != null ? strategy.getSelectedStrategy().name() : null);
        metadata.put("source", strategy.getSource() != null ? strategy.getSource().name() : null);
        metadata.put("confidence", strategy.getConfidence());
        metadata.put("riskLevel", strategy.getRiskLevel() != null ? strategy.getRiskLevel().name() : null);
        metadata.put("expectedRecoveryPercentage", strategy.getExpectedRecoveryPercentage());
        metadata.put("estimatedRecoveryAmount", strategy.getEstimatedRecoveryAmount());
        metadata.put("requiresHumanApproval", strategy.getRequiresHumanApproval());

        record(
                incident.getId(), null, strategy.getId(), null,
                AuditEventType.RECOVERY_STRATEGY_PROPOSED,
                AuditEventCategory.STRATEGY,
                AuditActorType.AI,
                "RECOVERY_STRATEGY_ENGINE",
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("Recovery strategy proposed: %s (Projected recovery: %.0f%%, Estimated ₹%s, Risk: %s)",
                        strategy.getSelectedStrategy(),
                        (strategy.getExpectedRecoveryPercentage() != null ? strategy.getExpectedRecoveryPercentage() : 0.0),
                        strategy.getEstimatedRecoveryAmount(), strategy.getRiskLevel()),
                strategy.getRationale(),
                strategy.getStoppingConditionsJson(),
                metadata
        );
    }

    public void recordPolicyEvaluation(Incident incident, PolicyEvaluation policy) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("policyEvaluationId", policy.getId());
        metadata.put("evaluatorVersion", policy.getEvaluatorVersion());
        metadata.put("decision", policy.getDecision() != null ? policy.getDecision().name() : null);
        metadata.put("executionAllowed", policy.getExecutionAllowed());
        metadata.put("riskLevel", policy.getRiskLevel() != null ? policy.getRiskLevel().name() : null);
        metadata.put("policyScore", policy.getPolicyScore());
        metadata.put("humanApprovalRequired", policy.getRequiredHumanApproval());

        AuditEventType specificType;
        AuditStatus status;
        AuditSeverity severity;

        if (policy.getDecision() == PolicyDecision.APPROVED) {
            specificType = AuditEventType.POLICY_APPROVED;
            status = AuditStatus.SUCCESS;
            severity = AuditSeverity.INFO;
        } else if (policy.getDecision() == PolicyDecision.APPROVED_WITH_LIMITS) {
            specificType = AuditEventType.POLICY_APPROVED_WITH_LIMITS;
            status = AuditStatus.SUCCESS;
            severity = AuditSeverity.INFO;
        } else if (policy.getDecision() == PolicyDecision.HUMAN_REVIEW) {
            specificType = AuditEventType.POLICY_HUMAN_REVIEW;
            status = AuditStatus.BLOCKED;
            severity = AuditSeverity.WARN;
        } else {
            specificType = AuditEventType.POLICY_REJECTED;
            status = AuditStatus.BLOCKED;
            severity = AuditSeverity.CRITICAL;
        }

        record(
                incident.getId(), null, policy.getStrategyId(), policy.getId(),
                specificType,
                AuditEventCategory.POLICY,
                AuditActorType.POLICY_ENGINE,
                policy.getEvaluatorVersion(),
                severity,
                status,
                String.format("Policy Gate %s: Execution %s (Score: %.0f/100, %s)",
                        policy.getDecision(), policy.getExecutionAllowed() ? "ALLOWED" : "BLOCKED",
                        policy.getPolicyScore(), policy.getSummary()),
                policy.getExecutionBlockedReason(),
                policy.getPolicyChecksJson(),
                metadata
        );
    }

    public void recordRecoveryExecutionRequested(Long incidentId) {
        record(
                incidentId, null, null, null,
                AuditEventType.RECOVERY_EXECUTION_REQUESTED,
                AuditEventCategory.EXECUTION,
                AuditActorType.USER,
                "OPERATOR_COMMAND",
                AuditSeverity.INFO,
                AuditStatus.INFO,
                String.format("Recovery execution requested by operator for incident #%d", incidentId),
                null,
                null,
                Map.of("incidentId", incidentId)
        );
    }

    public void recordRecoveryExecutionBlocked(Long incidentId, Long strategyId, Long policyId,
                                               StoppingReason reason, String details) {
        Map<String, Object> metadata = Map.of(
                "stoppingReason", reason != null ? reason.name() : "POLICY_BLOCKED",
                "details", details != null ? details : ""
        );

        record(
                incidentId, null, strategyId, policyId,
                AuditEventType.RECOVERY_EXECUTION_BLOCKED,
                AuditEventCategory.EXECUTION,
                AuditActorType.POLICY_ENGINE,
                "POLICY_GATE",
                AuditSeverity.WARN,
                AuditStatus.BLOCKED,
                String.format("Recovery execution BLOCKED for incident #%d: %s", incidentId, details),
                details,
                null,
                metadata
        );
    }

    public void recordRecoveryExecutionStarted(RecoveryExecution execution) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionId", execution.getId());
        metadata.put("strategyType", execution.getStrategyType() != null ? execution.getStrategyType().name() : null);
        metadata.put("appliedRetryLimit", execution.getAppliedRetryLimit());
        metadata.put("appliedRetryWindow", execution.getAppliedRetryWindow());
        metadata.put("appliedBackoff", execution.getAppliedBackoff());
        metadata.put("eligibleTransactions", execution.getEligibleTransactions());

        record(
                execution.getIncidentId(), execution.getId(), execution.getStrategyId(), execution.getPolicyEvaluationId(),
                AuditEventType.RECOVERY_EXECUTION_STARTED,
                AuditEventCategory.EXECUTION,
                AuditActorType.RECOVERY_ENGINE,
                execution.getSimulationVersion(),
                AuditSeverity.INFO,
                AuditStatus.INFO,
                String.format("Recovery simulation started for %d eligible transactions (Strategy: %s, Max retries: %d)",
                        execution.getEligibleTransactions(), execution.getStrategyType(), execution.getAppliedRetryLimit()),
                null,
                null,
                metadata
        );
    }

    public void recordRecoveryAttempt(RecoveryExecution execution, RecoveryExecutionAttempt attempt) {
        AuditEventType eventType = attempt.getOutcome() == com.sentinelpulse.recovery.RecoveryOutcome.RECOVERED
                ? AuditEventType.RECOVERY_SUCCEEDED
                : (attempt.getOutcome() == com.sentinelpulse.recovery.RecoveryOutcome.FAILED
                ? AuditEventType.RECOVERY_FAILED
                : AuditEventType.RECOVERY_ATTEMPTED);

        Map<String, Object> metadata = Map.of(
                "attemptNumber", attempt.getAttemptNumber(),
                "paymentId", attempt.getPaymentId(),
                "outcome", attempt.getOutcome() != null ? attempt.getOutcome().name() : null,
                "amount", attempt.getAmount()
        );

        record(
                execution.getIncidentId(), execution.getId(), execution.getStrategyId(), execution.getPolicyEvaluationId(),
                eventType,
                AuditEventCategory.EXECUTION,
                AuditActorType.RECOVERY_ENGINE,
                execution.getSimulationVersion(),
                AuditSeverity.INFO,
                attempt.getOutcome() == com.sentinelpulse.recovery.RecoveryOutcome.RECOVERED ? AuditStatus.SUCCESS : AuditStatus.FAILURE,
                String.format("Payment #%d attempt %d [%s]: %s (₹%s)",
                        attempt.getPaymentId(), attempt.getAttemptNumber(), attempt.getStrategyType(),
                        attempt.getOutcome(), attempt.getAmount()),
                attempt.getFailureReason(),
                null,
                metadata
        );
    }

    public void recordRecoveryExecutionCompleted(RecoveryExecution execution) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("executionId", execution.getId());
        metadata.put("status", execution.getStatus() != null ? execution.getStatus().name() : null);
        metadata.put("eligibleTransactions", execution.getEligibleTransactions());
        metadata.put("attemptedTransactions", execution.getAttemptedTransactions());
        metadata.put("successfulRecoveries", execution.getSuccessfulRecoveries());
        metadata.put("failedRecoveries", execution.getFailedRecoveries());
        metadata.put("recoveredAmount", execution.getRecoveredAmount());
        metadata.put("stoppingReason", execution.getStoppingReason() != null ? execution.getStoppingReason().name() : null);

        AuditEventType type = execution.getStatus() == ExecutionStatus.STOPPED
                ? AuditEventType.RECOVERY_STOPPED
                : AuditEventType.RECOVERY_COMPLETED;

        record(
                execution.getIncidentId(), execution.getId(), execution.getStrategyId(), execution.getPolicyEvaluationId(),
                type,
                AuditEventCategory.EXECUTION,
                AuditActorType.RECOVERY_ENGINE,
                execution.getSimulationVersion(),
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("Recovery simulation finished: ₹%s recovered across %d/%d transactions (%s)",
                        execution.getRecoveredAmount(), execution.getSuccessfulRecoveries(),
                        execution.getAttemptedTransactions(), execution.getStoppingReason()),
                execution.getStoppingDetails(),
                null,
                metadata
        );
    }

    public void recordIdempotentReuse(RecoveryExecution existingExecution) {
        Map<String, Object> metadata = Map.of(
                "existingExecutionId", existingExecution.getId(),
                "status", existingExecution.getStatus() != null ? existingExecution.getStatus().name() : null,
                "recoveredAmount", existingExecution.getRecoveredAmount()
        );

        record(
                existingExecution.getIncidentId(), existingExecution.getId(),
                existingExecution.getStrategyId(), existingExecution.getPolicyEvaluationId(),
                AuditEventType.EXECUTION_IDEMPOTENT_REUSE,
                AuditEventCategory.EXECUTION,
                AuditActorType.RECOVERY_ENGINE,
                existingExecution.getSimulationVersion(),
                AuditSeverity.INFO,
                AuditStatus.SUCCESS,
                String.format("Idempotent execution guard: Returning existing execution #%d (₹%s already recovered)",
                        existingExecution.getId(), existingExecution.getRecoveredAmount()),
                "Prevented duplicate execution & double-counting",
                null,
                metadata
        );
    }

    // =========================================================================
    // Query & Timeline Retrieval
    // =========================================================================

    @Transactional(readOnly = true)
    public AuditTimelineResponse getIncidentTimeline(Long incidentId) {
        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByIdAsc(incidentId);

        AuditVerificationResponse verification = verifyIncidentAuditChain(incidentId);
        AuditTimelineResponse.DecisionChainSummary decisionChain = buildDecisionChainSummary(events);

        Instant firstTs = events.isEmpty() ? null : events.get(0).getTimestamp();
        Instant lastTs = events.isEmpty() ? null : events.get(events.size() - 1).getTimestamp();

        List<AuditEventDto> dtos = events.stream().map(AuditEventDto::fromEntity).toList();

        return new AuditTimelineResponse(
                incidentId,
                null,
                events.size(),
                verification.valid(),
                firstTs,
                lastTs,
                decisionChain,
                dtos
        );
    }

    @Transactional(readOnly = true)
    public AuditTimelineResponse getExecutionTimeline(Long executionId) {
        List<AuditEvent> events = auditEventRepository.findByExecutionIdOrderByIdAsc(executionId);

        Instant firstTs = events.isEmpty() ? null : events.get(0).getTimestamp();
        Instant lastTs = events.isEmpty() ? null : events.get(events.size() - 1).getTimestamp();

        List<AuditEventDto> dtos = events.stream().map(AuditEventDto::fromEntity).toList();

        return new AuditTimelineResponse(
                events.isEmpty() ? null : events.get(0).getIncidentId(),
                executionId,
                events.size(),
                true,
                firstTs,
                lastTs,
                null,
                dtos
        );
    }

    // =========================================================================
    // Cryptographic Hash Chain Verification
    // =========================================================================

    @Transactional(readOnly = true)
    public AuditVerificationResponse verifyIncidentAuditChain(Long incidentId) {
        List<AuditEvent> events = auditEventRepository.findByIncidentIdOrderByIdAsc(incidentId);

        if (events.isEmpty()) {
            return new AuditVerificationResponse(
                    incidentId, true, 0, null, null, null,
                    "No audit events recorded for incident #" + incidentId
            );
        }

        Instant firstTs = events.get(0).getTimestamp();
        Instant lastTs = events.get(events.size() - 1).getTimestamp();

        String expectedPrevHash = null;

        for (AuditEvent event : events) {
            // Check 1: Verify previous event hash pointer link
            if (!Objects.equals(event.getPreviousEventHash(), expectedPrevHash)) {
                log.warn("Audit chain pointer break at event #{} for incident #{}: expected prev [{}] but got [{}]",
                        event.getId(), incidentId, expectedPrevHash, event.getPreviousEventHash());
                return new AuditVerificationResponse(
                        incidentId, false, events.size(), firstTs, lastTs, event.getId(),
                        "Audit chain link broken at event #" + event.getId() + " (" + event.getEventType() + ")"
                );
            }

            // Check 2: Verify current event hash matches payload calculation
            String computedHash = calculateEventHash(
                    event.getPreviousEventHash(),
                    event.getEventId(),
                    event.getIncidentId(),
                    event.getEventType(),
                    event.getTimestamp(),
                    event.getActorType(),
                    event.getSummary(),
                    event.getMetadataJson()
            );

            if (!Objects.equals(event.getEventHash(), computedHash)) {
                log.warn("Audit tampering detected at event #{} for incident #{}: computed [{}] vs recorded [{}]",
                        event.getId(), incidentId, computedHash, event.getEventHash());
                return new AuditVerificationResponse(
                        incidentId, false, events.size(), firstTs, lastTs, event.getId(),
                        "Cryptographic hash mismatch/tampering detected at event #" + event.getId()
                );
            }

            expectedPrevHash = event.getEventHash();
        }

        return new AuditVerificationResponse(
                incidentId, true, events.size(), firstTs, lastTs, null,
                String.format("Cryptographic audit chain verified successfully (%d events)", events.size())
        );
    }

    // =========================================================================
    // Canonical Hashing Algorithm
    // =========================================================================

    public String calculateEventHash(String previousEventHash, String eventId, Long incidentId,
                                     AuditEventType eventType, Instant timestamp,
                                     AuditActorType actorType, String summary,
                                     String metadataJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            String canonical = String.join("|",
                    previousEventHash != null ? previousEventHash : "GENESIS",
                    eventId != null ? eventId : "",
                    incidentId != null ? String.valueOf(incidentId) : "0",
                    eventType != null ? eventType.name() : "",
                    timestamp != null ? String.valueOf(timestamp.toEpochMilli()) : "",
                    actorType != null ? actorType.name() : "",
                    summary != null ? summary.trim() : "",
                    metadataJson != null ? metadataJson.trim() : ""
            );

            byte[] hashBytes = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private AuditTimelineResponse.DecisionChainSummary buildDecisionChainSummary(List<AuditEvent> events) {
        boolean detected = false;
        boolean diagnosed = false;
        boolean investigated = false;
        boolean strategyProposed = false;
        boolean policyApproved = false;
        boolean recoveryExecuted = false;
        BigDecimal recoveredAmount = BigDecimal.ZERO;
        String currentStage = "DETECTION";

        for (AuditEvent e : events) {
            switch (e.getEventType()) {
                case INCIDENT_DETECTED, INCIDENT_UPDATED -> {
                    detected = true;
                    currentStage = "DETECTED";
                }
                case AI_DIAGNOSIS_COMPLETED -> {
                    diagnosed = true;
                    currentStage = "DIAGNOSED";
                }
                case INVESTIGATION_COMPLETED -> {
                    investigated = true;
                    currentStage = "INVESTIGATED";
                }
                case RECOVERY_STRATEGY_PROPOSED -> {
                    strategyProposed = true;
                    currentStage = "STRATEGY_PROPOSED";
                }
                case POLICY_APPROVED, POLICY_APPROVED_WITH_LIMITS -> {
                    policyApproved = true;
                    currentStage = "POLICY_APPROVED";
                }
                case POLICY_REJECTED, POLICY_HUMAN_REVIEW -> {
                    currentStage = "POLICY_" + e.getEventType().name();
                }
                case RECOVERY_EXECUTION_STARTED -> {
                    recoveryExecuted = true;
                    currentStage = "EXECUTING_RECOVERY";
                }
                case RECOVERY_COMPLETED, RECOVERY_STOPPED -> {
                    recoveryExecuted = true;
                    currentStage = "RECOVERY_COMPLETED";
                    try {
                        if (e.getMetadataJson() != null && e.getMetadataJson().contains("recoveredAmount")) {
                            Map<?, ?> map = objectMapper.readValue(e.getMetadataJson(), Map.class);
                            Object amt = map.get("recoveredAmount");
                            if (amt != null) {
                                recoveredAmount = new BigDecimal(amt.toString());
                            }
                        }
                    } catch (Exception ignored) {}
                }
                default -> {}
            }
        }

        return new AuditTimelineResponse.DecisionChainSummary(
                detected, diagnosed, investigated, strategyProposed,
                policyApproved, recoveryExecuted, recoveredAmount, currentStage
        );
    }

    private String serializeToJson(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String str) return str;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Failed to serialize audit payload: {}", e.getMessage());
            return String.valueOf(obj);
        }
    }
}
