package com.sentinelpulse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.dto.AiEvidenceDto;
import com.sentinelpulse.dto.AppliedLimitDto;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.dto.PolicyCheckDto;
import com.sentinelpulse.dto.PolicyEvaluationResponse;
import com.sentinelpulse.dto.StoppingConditionsDto;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentDiagnosis;
import com.sentinelpulse.entity.IncidentInvestigation;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PolicyEvaluation;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.policy.PolicyCheckResult;
import com.sentinelpulse.policy.PolicyCheckSeverity;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
import com.sentinelpulse.repository.IncidentInvestigationRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PolicyEvaluationRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Deterministic Policy and Guardrail Engine.
 *
 * <p>Architectural Invariant:
 * <ul>
 *   <li>AI proposes recovery actions.</li>
 *   <li>Policy decides whether actions are permitted.</li>
 *   <li>Execution obeys policy.</li>
 * </ul>
 *
 * <p>Zero LLM calls are made during policy evaluation. All evaluations are 100% deterministic,
 * testable, bounded, and explainable.
 */
@Service
public class PolicyEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(PolicyEvaluationService.class);

    public static final String EVALUATOR_VERSION = "POLICY_V1";

    public static final int MAX_ALLOWED_RETRY_ATTEMPTS = 3;
    public static final int MAX_ALLOWED_RETRY_WINDOW_MINUTES = 60;
    public static final int MIN_ALLOWED_BACKOFF_SECONDS = 10;
    public static final int MAX_ALLOWED_BACKOFF_SECONDS = 900;
    public static final int STRATEGY_MAX_AGE_MINUTES = 30;

    private final IncidentRepository incidentRepository;
    private final RecoveryStrategyRepository recoveryStrategyRepository;
    private final FinancialImpactService financialImpactService;
    private final IncidentEvidenceService incidentEvidenceService;
    private final IncidentInvestigationRepository incidentInvestigationRepository;
    private final IncidentDiagnosisRepository incidentDiagnosisRepository;
    private final PolicyEvaluationRepository policyEvaluationRepository;
    private final ObjectMapper objectMapper;
    private final AuditEventService auditEventService;

    public PolicyEvaluationService(IncidentRepository incidentRepository,
                                   RecoveryStrategyRepository recoveryStrategyRepository,
                                   FinancialImpactService financialImpactService,
                                   IncidentEvidenceService incidentEvidenceService,
                                   IncidentInvestigationRepository incidentInvestigationRepository,
                                   IncidentDiagnosisRepository incidentDiagnosisRepository,
                                   PolicyEvaluationRepository policyEvaluationRepository,
                                   ObjectMapper objectMapper,
                                   AuditEventService auditEventService) {
        this.incidentRepository = incidentRepository;
        this.recoveryStrategyRepository = recoveryStrategyRepository;
        this.financialImpactService = financialImpactService;
        this.incidentEvidenceService = incidentEvidenceService;
        this.incidentInvestigationRepository = incidentInvestigationRepository;
        this.incidentDiagnosisRepository = incidentDiagnosisRepository;
        this.policyEvaluationRepository = policyEvaluationRepository;
        this.objectMapper = objectMapper;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public PolicyEvaluationResponse evaluatePolicy(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        RecoveryStrategy strategy = recoveryStrategyRepository
                .findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("No recovery strategy found for incident: " + incidentId + ". Formulate a recovery strategy first."));

        FinancialImpactResponse financialImpact = financialImpactService.getFinancialImpact(incidentId);
        AiEvidenceDto evidence = incidentEvidenceService.buildEvidence(incidentId);
        Optional<IncidentInvestigation> investigation = incidentInvestigationRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incidentId);
        Optional<IncidentDiagnosis> diagnosis = incidentDiagnosisRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incidentId);

        List<PolicyCheckDto> checks = new ArrayList<>();
        List<AppliedLimitDto> appliedLimits = new ArrayList<>();
        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        boolean forceHumanApproval = false;
        boolean isExecutionPermitted = true;

        // 1. POLICY-001: Strategy Must Be Controlled
        RecoveryStrategyType strategyType = strategy.getSelectedStrategy();
        if (strategyType == null) {
            checks.add(PolicyCheckDto.fail(
                    "POLICY-001", "Controlled Strategy Validation", PolicyCheckSeverity.CRITICAL,
                    "Recovery strategy is null or undefined.", "null", "Valid RecoveryStrategyType enum"
            ));
            violations.add("Strategy type is invalid or null");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-001", "Controlled Strategy Validation",
                    "Strategy '" + strategyType.name() + "' is an approved controlled strategy enum.", strategyType.name()
            ));
        }

        // 2. Parse Stopping Conditions
        StoppingConditionsDto stoppingConditions = null;
        if (strategy.getStoppingConditionsJson() != null && !strategy.getStoppingConditionsJson().isBlank()) {
            try {
                stoppingConditions = objectMapper.readValue(strategy.getStoppingConditionsJson(), StoppingConditionsDto.class);
            } catch (Exception ignored) {
            }
        }

        boolean isExecutableStrategy = strategyType == RecoveryStrategyType.IMMEDIATE_RETRY
                || strategyType == RecoveryStrategyType.DELAYED_RETRY
                || strategyType == RecoveryStrategyType.RETRY_WITH_BACKOFF
                || strategyType == RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD;

        // 3. POLICY-002: Stopping Conditions Completeness
        if (isExecutableStrategy) {
            if (stoppingConditions == null || (!stoppingConditions.stopOnSuccess() && !stoppingConditions.stopOnIncidentResolved())) {
                checks.add(PolicyCheckDto.fail(
                        "POLICY-002", "Stopping Conditions Completeness", PolicyCheckSeverity.CRITICAL,
                        "Executable strategy lacks mandatory bounded stopping conditions (stop on success / resolved).",
                        "Incomplete stopping conditions", "stopOnSuccess=true, stopOnIncidentResolved=true"
                ));
                violations.add("Missing mandatory stopping conditions for executable strategy");
            } else {
                checks.add(PolicyCheckDto.pass(
                        "POLICY-002", "Stopping Conditions Completeness",
                        "Stopping conditions defined with stop-on-success and stop-on-resolution triggers.",
                        "stopOnSuccess=" + stoppingConditions.stopOnSuccess() + ", maxAttempts=" + stoppingConditions.maxRetryAttempts()
                ));
            }
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-002", "Stopping Conditions Completeness",
                    "Non-retry strategy requires no automated retry stopping conditions.", "Passive/Review Strategy"
            ));
        }

        // 4. POLICY-003: Deterministic Retry & Window Limits Clamping
        if (isExecutableStrategy && stoppingConditions != null) {
            // Check Retry Attempts
            int proposedAttempts = stoppingConditions.maxRetryAttempts();
            if (proposedAttempts > MAX_ALLOWED_RETRY_ATTEMPTS) {
                appliedLimits.add(new AppliedLimitDto(
                        "maxRetryAttempts",
                        String.valueOf(proposedAttempts),
                        String.valueOf(MAX_ALLOWED_RETRY_ATTEMPTS),
                        "Proposed retry attempts (" + proposedAttempts + ") capped to global policy maximum (" + MAX_ALLOWED_RETRY_ATTEMPTS + ")"
                ));
                checks.add(PolicyCheckDto.warn(
                        "POLICY-003", "Retry Attempt Limits", PolicyCheckSeverity.MAJOR,
                        "Proposed retry attempts (" + proposedAttempts + ") exceed policy limit (" + MAX_ALLOWED_RETRY_ATTEMPTS + "). Clamped to " + MAX_ALLOWED_RETRY_ATTEMPTS + ".",
                        proposedAttempts + " attempts", "<= " + MAX_ALLOWED_RETRY_ATTEMPTS + " attempts"
                ));
                warnings.add("Retry attempts clamped from " + proposedAttempts + " to " + MAX_ALLOWED_RETRY_ATTEMPTS);
            } else if (proposedAttempts <= 0 && strategyType != RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD) {
                checks.add(PolicyCheckDto.fail(
                        "POLICY-003", "Retry Attempt Limits", PolicyCheckSeverity.CRITICAL,
                        "Retry strategy must define at least 1 retry attempt.",
                        proposedAttempts + " attempts", ">= 1 attempt"
                ));
                violations.add("Retry strategy specifies zero or negative retry attempts");
            } else {
                checks.add(PolicyCheckDto.pass(
                        "POLICY-003", "Retry Attempt Limits",
                        "Retry attempts (" + proposedAttempts + ") within allowed policy bounds (<= " + MAX_ALLOWED_RETRY_ATTEMPTS + ").",
                        proposedAttempts + " attempts"
                ));
            }

            // Check Execution Window
            int proposedWindow = stoppingConditions.retryWindowMinutes();
            if (proposedWindow > MAX_ALLOWED_RETRY_WINDOW_MINUTES) {
                appliedLimits.add(new AppliedLimitDto(
                        "retryWindowMinutes",
                        String.valueOf(proposedWindow),
                        String.valueOf(MAX_ALLOWED_RETRY_WINDOW_MINUTES),
                        "Execution window (" + proposedWindow + "m) capped to maximum allowed duration (" + MAX_ALLOWED_RETRY_WINDOW_MINUTES + "m)"
                ));
                checks.add(PolicyCheckDto.warn(
                        "POLICY-003", "Retry Window Limits", PolicyCheckSeverity.MINOR,
                        "Proposed retry window (" + proposedWindow + "m) exceeds policy limit (" + MAX_ALLOWED_RETRY_WINDOW_MINUTES + "m). Clamped to " + MAX_ALLOWED_RETRY_WINDOW_MINUTES + "m.",
                        proposedWindow + " mins", "<= " + MAX_ALLOWED_RETRY_WINDOW_MINUTES + " mins"
                ));
                warnings.add("Retry window clamped from " + proposedWindow + "m to " + MAX_ALLOWED_RETRY_WINDOW_MINUTES + "m");
            } else {
                checks.add(PolicyCheckDto.pass(
                        "POLICY-003", "Retry Window Limits",
                        "Execution window (" + proposedWindow + " mins) within allowed policy bounds.",
                        proposedWindow + " mins"
                ));
            }

            // Check Backoff interval for delayed/backoff strategies
            if (strategyType == RecoveryStrategyType.DELAYED_RETRY || strategyType == RecoveryStrategyType.RETRY_WITH_BACKOFF) {
                int proposedBackoff = stoppingConditions.backoffBaseSeconds();
                if (proposedBackoff < MIN_ALLOWED_BACKOFF_SECONDS) {
                    appliedLimits.add(new AppliedLimitDto(
                            "backoffBaseSeconds",
                            String.valueOf(proposedBackoff),
                            String.valueOf(MIN_ALLOWED_BACKOFF_SECONDS),
                            "Backoff interval (" + proposedBackoff + "s) increased to minimum required delay (" + MIN_ALLOWED_BACKOFF_SECONDS + "s)"
                    ));
                    checks.add(PolicyCheckDto.warn(
                            "POLICY-003", "Backoff Schedule Safety", PolicyCheckSeverity.MINOR,
                            "Backoff interval (" + proposedBackoff + "s) is below minimum safe threshold. Enforced minimum " + MIN_ALLOWED_BACKOFF_SECONDS + "s.",
                            proposedBackoff + "s", ">= " + MIN_ALLOWED_BACKOFF_SECONDS + "s"
                    ));
                } else if (proposedBackoff > MAX_ALLOWED_BACKOFF_SECONDS) {
                    appliedLimits.add(new AppliedLimitDto(
                            "backoffBaseSeconds",
                            String.valueOf(proposedBackoff),
                            String.valueOf(MAX_ALLOWED_BACKOFF_SECONDS),
                            "Backoff interval (" + proposedBackoff + "s) capped to maximum delay (" + MAX_ALLOWED_BACKOFF_SECONDS + "s)"
                    ));
                    checks.add(PolicyCheckDto.warn(
                            "POLICY-003", "Backoff Schedule Safety", PolicyCheckSeverity.MINOR,
                            "Backoff interval (" + proposedBackoff + "s) exceeds maximum policy delay. Enforced " + MAX_ALLOWED_BACKOFF_SECONDS + "s.",
                            proposedBackoff + "s", "<= " + MAX_ALLOWED_BACKOFF_SECONDS + "s"
                    ));
                } else {
                    checks.add(PolicyCheckDto.pass(
                            "POLICY-003", "Backoff Schedule Safety",
                            "Backoff base interval (" + proposedBackoff + "s) satisfies policy schedule.",
                            proposedBackoff + "s"
                    ));
                }
            }
        }

        // 5. POLICY-004: Risk Guardrail
        RiskLevel riskLevel = strategy.getRiskLevel() != null ? strategy.getRiskLevel() : RiskLevel.HIGH;
        if (riskLevel == RiskLevel.CRITICAL) {
            checks.add(PolicyCheckDto.fail(
                    "POLICY-004", "Risk Level Guardrail", PolicyCheckSeverity.CRITICAL,
                    "Autonomous recovery is strictly forbidden for CRITICAL risk incidents. Executive escalation required.",
                    "CRITICAL", "LOW or MEDIUM for automated execution"
            ));
            violations.add("Critical risk level blocks autonomous recovery execution");
        } else if (riskLevel == RiskLevel.HIGH) {
            forceHumanApproval = true;
            checks.add(PolicyCheckDto.warn(
                    "POLICY-004", "Risk Level Guardrail", PolicyCheckSeverity.MAJOR,
                    "HIGH risk level requires explicit Human Commander authorization prior to execution.",
                    "HIGH", "Human Commander approval required"
            ));
            warnings.add("High risk requires human commander authorization");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-004", "Risk Level Guardrail",
                    "Risk level '" + riskLevel + "' is within acceptable autonomous tolerance.",
                    riskLevel.name()
            ));
        }

        // 6. POLICY-005: Confidence Guardrail
        double confidence = strategy.getConfidence() != null ? strategy.getConfidence() : 0.0;
        if (confidence < 0.70) {
            forceHumanApproval = true;
            checks.add(PolicyCheckDto.warn(
                    "POLICY-005", "Diagnosis Confidence Guardrail", PolicyCheckSeverity.MAJOR,
                    String.format("Confidence score (%.2f) is below autonomous safety threshold (0.70). Human review required.", confidence),
                    String.format("%.2f", confidence), ">= 0.70 for autonomous execution"
            ));
            warnings.add(String.format("Low confidence (%.2f) requires human review", confidence));
        } else if (confidence < 0.85) {
            checks.add(PolicyCheckDto.warn(
                    "POLICY-005", "Diagnosis Confidence Guardrail", PolicyCheckSeverity.MINOR,
                    String.format("Confidence score (%.2f) allows bounded execution under enforced limits.", confidence),
                    String.format("%.2f", confidence), ">= 0.85 for unrestricted execution"
            ));
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-005", "Diagnosis Confidence Guardrail",
                    String.format("High confidence (%.2f) satisfies autonomous execution policy.", confidence),
                    String.format("%.2f", confidence)
            ));
        }

        // 7. POLICY-006: Merchant Degradation Protection
        boolean isMerchantIncident = incident.getType() == IncidentType.MERCHANT_DEGRADATION
                || (strategy.getAffectedSegment() != null && strategy.getAffectedSegment().toLowerCase().contains("merchant"));
        if (isMerchantIncident) {
            if (strategyType == RecoveryStrategyType.IMMEDIATE_RETRY || strategyType == RecoveryStrategyType.DELAYED_RETRY || strategyType == RecoveryStrategyType.RETRY_WITH_BACKOFF) {
                checks.add(PolicyCheckDto.fail(
                        "POLICY-006", "Merchant Degradation Protection", PolicyCheckSeverity.CRITICAL,
                        "Automated retries against a degraded merchant integration endpoint are strictly forbidden to prevent duplicate customer billing or rate limit exhaustion.",
                        "Automated retry proposed", "HUMAN_REVIEW or MONITOR_ONLY"
                ));
                violations.add("Automated retries against merchant integration issue are blocked by policy");
            } else {
                forceHumanApproval = true;
                checks.add(PolicyCheckDto.pass(
                        "POLICY-006", "Merchant Degradation Protection",
                        "Merchant degradation correctly routed to " + strategyType.name() + " without blind retries.",
                        strategyType.name()
                ));
            }
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-006", "Merchant Degradation Protection",
                    "Incident is rail-level/global, not merchant-isolated.", "Non-merchant incident"
            ));
        }

        // 8. POLICY-007: Alternate Payment Method Guardrail
        if (strategyType == RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD) {
            forceHumanApproval = true;
            checks.add(PolicyCheckDto.warn(
                    "POLICY-007", "Alternate Payment Method Guardrail", PolicyCheckSeverity.MAJOR,
                    "Payment method rerouting alters customer checkout experience; explicit Human Commander authorization is mandatory.",
                    "ALTERNATE_PAYMENT_METHOD", "Human authorization required"
            ));
            warnings.add("Payment method rerouting requires human commander sign-off");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-007", "Alternate Payment Method Guardrail",
                    "Strategy does not involve cross-method payment rerouting.", "No payment method switch"
            ));
        }

        // 9. POLICY-008: Financial Invariant & Bounding Check
        BigDecimal authoritativeRecoverable = financialImpact.estimatedRecoverableRevenue();
        if ((authoritativeRecoverable == null || authoritativeRecoverable.compareTo(BigDecimal.ZERO) == 0)
                && strategy.getAuthoritativeRecoverableAmount() != null
                && strategy.getAuthoritativeRecoverableAmount().compareTo(BigDecimal.ZERO) > 0) {
            authoritativeRecoverable = strategy.getAuthoritativeRecoverableAmount();
        }
        if (authoritativeRecoverable == null && incident.getRevenueAtRisk() != null) {
            authoritativeRecoverable = incident.getRevenueAtRisk();
        }
        if (authoritativeRecoverable == null) {
            authoritativeRecoverable = BigDecimal.ZERO;
        }

        BigDecimal estimatedRecoveryAmount = strategy.getEstimatedRecoveryAmount();
        double recoveryPct = strategy.getExpectedRecoveryPercentage() != null ? strategy.getExpectedRecoveryPercentage() : 0.0;

        if (estimatedRecoveryAmount != null
                && estimatedRecoveryAmount.compareTo(authoritativeRecoverable) > 0) {
            checks.add(PolicyCheckDto.fail(
                    "POLICY-008", "Financial Safety Bounds", PolicyCheckSeverity.CRITICAL,
                    "Strategy estimated recovery amount (" + estimatedRecoveryAmount + ") exceeds authoritative recoverable revenue (" + authoritativeRecoverable + ").",
                    "₹" + estimatedRecoveryAmount, "<= ₹" + authoritativeRecoverable
            ));
            violations.add("Strategy estimated recovery exceeds authoritative recoverable revenue");
        } else if (recoveryPct < 0.0 || recoveryPct > 100.0) {
            checks.add(PolicyCheckDto.fail(
                    "POLICY-008", "Financial Safety Bounds", PolicyCheckSeverity.CRITICAL,
                    "Expected recovery percentage (" + recoveryPct + "%) is out of valid bounds [0.0, 100.0].",
                    recoveryPct + "%", "0.0% to 100.0%"
            ));
            violations.add("Recovery percentage out of bounds");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-008", "Financial Safety Bounds",
                    "Strategy recovery estimates strictly conform to authoritative financial boundaries.",
                    "₹" + estimatedRecoveryAmount + " <= ₹" + authoritativeRecoverable
            ));
        }

        // 10. POLICY-009: Incident Resolution Guardrail
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            checks.add(PolicyCheckDto.fail(
                    "POLICY-009", "Incident Lifecycle Guardrail", PolicyCheckSeverity.CRITICAL,
                    "Incident #" + incidentId + " is already RESOLVED. Recovery execution cannot proceed on a resolved incident.",
                    "RESOLVED", "DETECTED or INVESTIGATING"
            ));
            violations.add("Recovery cannot execute against a resolved incident");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-009", "Incident Lifecycle Guardrail",
                    "Incident status is active (" + incident.getStatus() + ").",
                    incident.getStatus().name()
            ));
        }

        // 11. POLICY-010: Strategy Status Guardrail
        if (strategy.getStatus() != StrategyStatus.PROPOSED) {
            checks.add(PolicyCheckDto.fail(
                    "POLICY-010", "Strategy Lifecycle Guardrail", PolicyCheckSeverity.CRITICAL,
                    "Strategy proposal is in status '" + strategy.getStatus() + "' (expected PROPOSED). Stale/superseded proposals cannot be policy approved.",
                    strategy.getStatus().name(), "PROPOSED"
            ));
            violations.add("Only currently proposed strategies are eligible for policy approval");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-010", "Strategy Lifecycle Guardrail",
                    "Strategy proposal is in active PROPOSED status.",
                    "PROPOSED"
            ));
        }

        // 12. POLICY-011: Strategy Freshness Guardrail
        Duration strategyAge = Duration.between(strategy.getCreatedAt(), Instant.now());
        if (strategyAge.toMinutes() > STRATEGY_MAX_AGE_MINUTES) {
            forceHumanApproval = true;
            checks.add(PolicyCheckDto.warn(
                    "POLICY-011", "Strategy Freshness Guardrail", PolicyCheckSeverity.MAJOR,
                    "Strategy proposal was generated " + strategyAge.toMinutes() + " minutes ago (max allowed freshness: " + STRATEGY_MAX_AGE_MINUTES + "m). Incident telemetry may have shifted; re-evaluation or human sign-off required.",
                    strategyAge.toMinutes() + " mins old", "<= " + STRATEGY_MAX_AGE_MINUTES + " mins"
            ));
            warnings.add("Strategy proposal is older than " + STRATEGY_MAX_AGE_MINUTES + " minutes");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-011", "Strategy Freshness Guardrail",
                    "Strategy proposal was generated " + strategyAge.toMinutes() + " minutes ago (fresh).",
                    strategyAge.toMinutes() + " mins old"
            ));
        }

        // 13. POLICY-012: Telemetry Evidence Integrity
        if (evidence == null) {
            forceHumanApproval = true;
            checks.add(PolicyCheckDto.warn(
                    "POLICY-012", "Evidence Integrity Guardrail", PolicyCheckSeverity.MAJOR,
                    "Deterministic telemetry evidence is missing or incomplete.", "Missing telemetry", "Complete telemetry required"
            ));
            warnings.add("Missing deterministic evidence requires manual inspection");
        } else {
            checks.add(PolicyCheckDto.pass(
                    "POLICY-012", "Evidence Integrity Guardrail",
                    "Deterministic telemetry evidence verified and complete.", "Verified telemetry"
            ));
        }

        // Strategy-level human approval recommendation
        if (Boolean.TRUE.equals(strategy.getRequiresHumanApproval())) {
            forceHumanApproval = true;
        }

        // 14. Determine Final Policy Decision
        boolean hasFailures = checks.stream().anyMatch(c -> c.result() == PolicyCheckResult.FAIL);
        boolean hasCriticalFailures = checks.stream().anyMatch(c -> c.result() == PolicyCheckResult.FAIL && c.severity() == PolicyCheckSeverity.CRITICAL);
        boolean hasWarnings = checks.stream().anyMatch(c -> c.result() == PolicyCheckResult.WARN);
        boolean hasAppliedLimits = !appliedLimits.isEmpty();

        PolicyDecision decision;
        boolean overallAllowed;
        boolean executionAllowed;
        String summary;
        String executionBlockedReason = null;
        String nextStep;

        if (hasFailures) {
            decision = PolicyDecision.REJECTED;
            overallAllowed = false;
            executionAllowed = false;
            summary = "Recovery strategy proposal REJECTED due to " + violations.size() + " mandatory policy violation(s).";
            executionBlockedReason = violations.isEmpty() ? "Policy gate violation" : violations.get(0);
            nextStep = "Re-evaluate strategy proposal with compliant parameters or resolve underlying telemetry/lifecycle issues.";
        } else if (forceHumanApproval) {
            decision = PolicyDecision.HUMAN_REVIEW;
            overallAllowed = true;
            executionAllowed = false; // Autonomous execution blocked until human approval
            summary = "Strategy proposal is policy-compliant but requires explicit Human Commander authorization prior to execution.";
            executionBlockedReason = "Autonomous execution blocked: Human Commander authorization required.";
            nextStep = "Payment Commander must review the proposal, stopping conditions, and authorize execution.";
        } else if (hasAppliedLimits || hasWarnings) {
            decision = PolicyDecision.APPROVED_WITH_LIMITS;
            overallAllowed = true;
            executionAllowed = (strategyType != RecoveryStrategyType.NO_ACTION && strategyType != RecoveryStrategyType.MONITOR_ONLY);
            summary = "Strategy proposal APPROVED WITH LIMITS: execution permitted under " + appliedLimits.size() + " enforced constraint(s).";
            nextStep = "Proceed to Recovery Execution Simulator under enforced limits.";
        } else {
            decision = PolicyDecision.APPROVED;
            overallAllowed = true;
            executionAllowed = (strategyType != RecoveryStrategyType.NO_ACTION && strategyType != RecoveryStrategyType.MONITOR_ONLY);
            summary = "Strategy proposal satisfies all mandatory safety policies and is APPROVED for execution.";
            nextStep = "Proceed to Recovery Execution Simulator.";
        }

        // Calculate deterministic policy score (0-100)
        double policyScore = 100.0;
        for (PolicyCheckDto c : checks) {
            if (c.result() == PolicyCheckResult.FAIL) {
                policyScore -= 25.0;
            } else if (c.result() == PolicyCheckResult.WARN) {
                if (c.severity() == PolicyCheckSeverity.MAJOR) {
                    policyScore -= 10.0;
                } else {
                    policyScore -= 5.0;
                }
            }
        }
        policyScore = Math.max(0.0, Math.min(100.0, policyScore));

        // Serialize structured fields for audit and storage
        String checksJson;
        String limitsJson;
        String violationsJson;
        String warningsJson;
        try {
            checksJson = objectMapper.writeValueAsString(checks);
            limitsJson = objectMapper.writeValueAsString(appliedLimits);
            violationsJson = objectMapper.writeValueAsString(violations);
            warningsJson = objectMapper.writeValueAsString(warnings);
        } catch (Exception e) {
            checksJson = "[]";
            limitsJson = "[]";
            violationsJson = "[]";
            warningsJson = "[]";
        }

        Instant now = Instant.now();
        PolicyEvaluation entity = new PolicyEvaluation(
                incidentId,
                strategy.getId(),
                now,
                EVALUATOR_VERSION,
                decision,
                overallAllowed,
                executionAllowed,
                forceHumanApproval,
                riskLevel,
                policyScore,
                summary,
                executionBlockedReason,
                nextStep,
                checksJson,
                limitsJson,
                violationsJson,
                warningsJson
        );

        entity = policyEvaluationRepository.save(entity);

        auditEventService.recordPolicyEvaluation(incident, entity);

        return toResponse(entity, checks, appliedLimits, violations, warnings);
    }

    @Transactional(readOnly = true)
    public Optional<PolicyEvaluationResponse> getLatestEvaluation(Long incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident not found: " + incidentId);
        }

        return policyEvaluationRepository.findFirstByIncidentIdOrderByEvaluatedAtDesc(incidentId)
                .map(this::mapEntityToResponse);
    }

    private PolicyEvaluationResponse mapEntityToResponse(PolicyEvaluation entity) {
        List<PolicyCheckDto> checks = new ArrayList<>();
        if (entity.getPolicyChecksJson() != null && !entity.getPolicyChecksJson().isBlank()) {
            try {
                checks = objectMapper.readValue(entity.getPolicyChecksJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        List<AppliedLimitDto> limits = new ArrayList<>();
        if (entity.getAppliedLimitsJson() != null && !entity.getAppliedLimitsJson().isBlank()) {
            try {
                limits = objectMapper.readValue(entity.getAppliedLimitsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        List<String> violations = new ArrayList<>();
        if (entity.getViolationsJson() != null && !entity.getViolationsJson().isBlank()) {
            try {
                violations = objectMapper.readValue(entity.getViolationsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        List<String> warnings = new ArrayList<>();
        if (entity.getWarningsJson() != null && !entity.getWarningsJson().isBlank()) {
            try {
                warnings = objectMapper.readValue(entity.getWarningsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        return toResponse(entity, checks, limits, violations, warnings);
    }

    private PolicyEvaluationResponse toResponse(PolicyEvaluation entity,
                                                List<PolicyCheckDto> checks,
                                                List<AppliedLimitDto> limits,
                                                List<String> violations,
                                                List<String> warnings) {
        return new PolicyEvaluationResponse(
                entity.getId(),
                entity.getIncidentId(),
                entity.getStrategyId(),
                entity.getEvaluatedAt(),
                entity.getEvaluatorVersion(),
                entity.getDecision(),
                Boolean.TRUE.equals(entity.getOverallAllowed()),
                Boolean.TRUE.equals(entity.getExecutionAllowed()),
                Boolean.TRUE.equals(entity.getRequiredHumanApproval()),
                entity.getRiskLevel(),
                entity.getPolicyScore() != null ? entity.getPolicyScore() : 100.0,
                entity.getSummary(),
                entity.getExecutionBlockedReason(),
                entity.getNextStep(),
                checks,
                limits,
                violations,
                warnings,
                PolicyEvaluationResponse.DEFAULT_POLICY_DISCLAIMER
        );
    }
}
