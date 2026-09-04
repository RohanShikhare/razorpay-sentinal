package com.sentinelpulse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.dto.AppliedLimitDto;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.dto.RecoveryExecutionAttemptDto;
import com.sentinelpulse.dto.RecoveryExecutionResponse;
import com.sentinelpulse.dto.RecoveryMetricsDto;
import com.sentinelpulse.dto.StoppingConditionsDto;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.entity.PolicyEvaluation;
import com.sentinelpulse.entity.RecoveryExecution;
import com.sentinelpulse.entity.RecoveryExecutionAttempt;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.recovery.RecoveryOutcome;
import com.sentinelpulse.recovery.StoppingReason;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PaymentSpecifications;
import com.sentinelpulse.repository.PolicyEvaluationRepository;
import com.sentinelpulse.repository.RecoveryExecutionAttemptRepository;
import com.sentinelpulse.repository.RecoveryExecutionRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bounded Recovery Execution Simulator Service (Module 10).
 *
 * <p>Strict Architectural Boundary:
 * <ul>
 *   <li>AI Proposes recovery actions (Modules 6, 7, 8).</li>
 *   <li>Policy Gate Decides whether actions are permitted (Module 9).</li>
 *   <li>Execution Simulator strictly Obeys policy (Module 10).</li>
 * </ul>
 *
 * <p>Zero live gateway API calls or money movements are performed. All executions are simulated
 * against the synthetic payment dataset under deterministic, policy-enforced constraints.
 */
@Service
public class RecoveryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RecoveryExecutionService.class);

    public static final String SIMULATION_VERSION = "SIM_V1";
    public static final double DEFAULT_CIRCUIT_BREAKER_FAILURE_RATE = 50.0;

    private final IncidentRepository incidentRepository;
    private final RecoveryStrategyRepository recoveryStrategyRepository;
    private final PolicyEvaluationRepository policyEvaluationRepository;
    private final RecoveryExecutionRepository recoveryExecutionRepository;
    private final RecoveryExecutionAttemptRepository recoveryExecutionAttemptRepository;
    private final PaymentRepository paymentRepository;
    private final FinancialImpactService financialImpactService;
    private final ObjectMapper objectMapper;
    private final AuditEventService auditEventService;

    public RecoveryExecutionService(IncidentRepository incidentRepository,
                                    RecoveryStrategyRepository recoveryStrategyRepository,
                                    PolicyEvaluationRepository policyEvaluationRepository,
                                    RecoveryExecutionRepository recoveryExecutionRepository,
                                    RecoveryExecutionAttemptRepository recoveryExecutionAttemptRepository,
                                    PaymentRepository paymentRepository,
                                    FinancialImpactService financialImpactService,
                                    ObjectMapper objectMapper,
                                    AuditEventService auditEventService) {
        this.incidentRepository = incidentRepository;
        this.recoveryStrategyRepository = recoveryStrategyRepository;
        this.policyEvaluationRepository = policyEvaluationRepository;
        this.recoveryExecutionRepository = recoveryExecutionRepository;
        this.recoveryExecutionAttemptRepository = recoveryExecutionAttemptRepository;
        this.paymentRepository = paymentRepository;
        this.financialImpactService = financialImpactService;
        this.objectMapper = objectMapper;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public RecoveryExecutionResponse executeRecovery(Long incidentId) {
        Instant startTime = Instant.now();

        auditEventService.recordRecoveryExecutionRequested(incidentId);

        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        RecoveryStrategy strategy = recoveryStrategyRepository
                .findFirstByIncidentIdOrderByCreatedAtDesc(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("No recovery strategy found for incident: " + incidentId));

        PolicyEvaluation policyEvaluation = policyEvaluationRepository
                .findFirstByIncidentIdOrderByEvaluatedAtDesc(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("No policy evaluation found for incident: " + incidentId + ". Run Module 9 Policy Gate first."));

        FinancialImpactResponse financialImpact = financialImpactService.getFinancialImpact(incidentId);

        // Authoritative recoverable amount resolution
        BigDecimal authoritativeRevenueAtRisk = financialImpact.revenueAtRisk() != null
                ? financialImpact.revenueAtRisk() : incident.getRevenueAtRisk();
        BigDecimal authoritativeRecoverableAmount = financialImpact.estimatedRecoverableRevenue();
        if ((authoritativeRecoverableAmount == null || authoritativeRecoverableAmount.compareTo(BigDecimal.ZERO) == 0)
                && strategy.getAuthoritativeRecoverableAmount() != null
                && strategy.getAuthoritativeRecoverableAmount().compareTo(BigDecimal.ZERO) > 0) {
            authoritativeRecoverableAmount = strategy.getAuthoritativeRecoverableAmount();
        }
        if (authoritativeRecoverableAmount == null && incident.getRevenueAtRisk() != null) {
            authoritativeRecoverableAmount = incident.getRevenueAtRisk();
        }
        if (authoritativeRecoverableAmount == null) {
            authoritativeRecoverableAmount = BigDecimal.ZERO;
        }

        // 1. Verify Strategy Consistency & Freshness
        if (!strategy.getId().equals(policyEvaluation.getStrategyId())) {
            return createBlockedExecution(
                    incidentId, strategy.getId(), policyEvaluation.getId(), strategy.getSelectedStrategy(),
                    authoritativeRevenueAtRisk, authoritativeRecoverableAmount, strategy.getExpectedRecoveryPercentage(),
                    StoppingReason.POLICY_BLOCKED, "Policy evaluation strategy ID (" + policyEvaluation.getStrategyId() + ") does not match latest proposed strategy (" + strategy.getId() + "). Re-evaluate policy gate.",
                    startTime
            );
        }

        if (strategy.getStatus() != StrategyStatus.PROPOSED) {
            return createBlockedExecution(
                    incidentId, strategy.getId(), policyEvaluation.getId(), strategy.getSelectedStrategy(),
                    authoritativeRevenueAtRisk, authoritativeRecoverableAmount, strategy.getExpectedRecoveryPercentage(),
                    StoppingReason.POLICY_BLOCKED, "Proposed strategy is in status '" + strategy.getStatus() + "' (expected PROPOSED).",
                    startTime
            );
        }

        // 2. Verify Incident Lifecycle
        if (incident.getStatus() == IncidentStatus.RESOLVED) {
            return createBlockedExecution(
                    incidentId, strategy.getId(), policyEvaluation.getId(), strategy.getSelectedStrategy(),
                    authoritativeRevenueAtRisk, authoritativeRecoverableAmount, strategy.getExpectedRecoveryPercentage(),
                    StoppingReason.INCIDENT_RESOLVED, "Incident #" + incidentId + " is already RESOLVED. Recovery execution blocked.",
                    startTime
            );
        }

        // 3. Verify Server-Side Policy Gate Decision
        boolean executionAllowed = Boolean.TRUE.equals(policyEvaluation.getExecutionAllowed());
        PolicyDecision decision = policyEvaluation.getDecision();

        if (decision == PolicyDecision.HUMAN_REVIEW || Boolean.TRUE.equals(policyEvaluation.getRequiredHumanApproval())) {
            return createBlockedExecution(
                    incidentId, strategy.getId(), policyEvaluation.getId(), strategy.getSelectedStrategy(),
                    authoritativeRevenueAtRisk, authoritativeRecoverableAmount, strategy.getExpectedRecoveryPercentage(),
                    StoppingReason.HUMAN_APPROVAL_REQUIRED, "Policy Gate requires explicit Human Commander authorization prior to execution.",
                    startTime
            );
        }

        if (decision == PolicyDecision.REJECTED || !executionAllowed) {
            return createBlockedExecution(
                    incidentId, strategy.getId(), policyEvaluation.getId(), strategy.getSelectedStrategy(),
                    authoritativeRevenueAtRisk, authoritativeRecoverableAmount, strategy.getExpectedRecoveryPercentage(),
                    StoppingReason.POLICY_BLOCKED, policyEvaluation.getExecutionBlockedReason() != null
                            ? policyEvaluation.getExecutionBlockedReason() : "Policy Gate rejected recovery execution.",
                    startTime
            );
        }

        // 4. Idempotency Check: Return existing completed execution if already run for this strategy
        Optional<RecoveryExecution> existingExecution = recoveryExecutionRepository
                .findFirstByStrategyIdOrderByStartedAtDesc(strategy.getId());
        if (existingExecution.isPresent()) {
            RecoveryExecution existing = existingExecution.get();
            if (existing.getStatus() == ExecutionStatus.COMPLETED || existing.getStatus() == ExecutionStatus.STOPPED) {
                log.info("Returning existing recovery execution #{} for strategy #{}", existing.getId(), strategy.getId());
                auditEventService.recordIdempotentReuse(existing);
                List<RecoveryExecutionAttempt> attempts = recoveryExecutionAttemptRepository
                        .findAllByExecutionIdOrderByAttemptNumberAsc(existing.getId());
                return mapToResponse(existing, attempts);
            }
        }

        // 5. Parse Enforced Policy Limits (Module 9 applied limits override Module 8 proposals)
        StoppingConditionsDto proposedConditions = null;
        if (strategy.getStoppingConditionsJson() != null && !strategy.getStoppingConditionsJson().isBlank()) {
            try {
                proposedConditions = objectMapper.readValue(strategy.getStoppingConditionsJson(), StoppingConditionsDto.class);
            } catch (Exception ignored) {
            }
        }

        List<AppliedLimitDto> appliedLimits = new ArrayList<>();
        if (policyEvaluation.getAppliedLimitsJson() != null && !policyEvaluation.getAppliedLimitsJson().isBlank()) {
            try {
                appliedLimits = objectMapper.readValue(policyEvaluation.getAppliedLimitsJson(), new TypeReference<>() {});
            } catch (Exception ignored) {
            }
        }

        int maxRetryAttempts = proposedConditions != null ? proposedConditions.maxRetryAttempts() : 1;
        int retryWindowMinutes = proposedConditions != null ? proposedConditions.retryWindowMinutes() : 30;
        int backoffBaseSeconds = proposedConditions != null ? proposedConditions.backoffBaseSeconds() : 30;
        double circuitBreakerThreshold = (proposedConditions != null && proposedConditions.failureRateThreshold() > 0)
                ? proposedConditions.failureRateThreshold() : DEFAULT_CIRCUIT_BREAKER_FAILURE_RATE;

        // Apply clamped limits from Module 9 Policy Gate
        for (AppliedLimitDto limit : appliedLimits) {
            if ("maxRetryAttempts".equals(limit.parameter())) {
                try {
                    maxRetryAttempts = Integer.parseInt(limit.enforcedValue());
                } catch (NumberFormatException ignored) {
                }
            } else if ("retryWindowMinutes".equals(limit.parameter())) {
                try {
                    retryWindowMinutes = Integer.parseInt(limit.enforcedValue());
                } catch (NumberFormatException ignored) {
                }
            } else if ("backoffBaseSeconds".equals(limit.parameter())) {
                try {
                    backoffBaseSeconds = Integer.parseInt(limit.enforcedValue());
                } catch (NumberFormatException ignored) {
                }
            }
        }

        // Hard upper bounds
        maxRetryAttempts = Math.min(maxRetryAttempts, PolicyEvaluationService.MAX_ALLOWED_RETRY_ATTEMPTS);
        retryWindowMinutes = Math.min(retryWindowMinutes, PolicyEvaluationService.MAX_ALLOWED_RETRY_WINDOW_MINUTES);
        backoffBaseSeconds = Math.max(backoffBaseSeconds, PolicyEvaluationService.MIN_ALLOWED_BACKOFF_SECONDS);

        // 6. Handle Passive Strategies (MONITOR_ONLY, NO_ACTION)
        RecoveryStrategyType strategyType = strategy.getSelectedStrategy();
        if (strategyType == RecoveryStrategyType.MONITOR_ONLY || strategyType == RecoveryStrategyType.NO_ACTION) {
            RecoveryExecution passiveExecution = new RecoveryExecution(
                    incidentId,
                    strategy.getId(),
                    policyEvaluation.getId(),
                    startTime,
                    Instant.now(),
                    ExecutionStatus.COMPLETED,
                    strategyType,
                    0L, 0L, 0L, 0L, 0L,
                    BigDecimal.ZERO, BigDecimal.ZERO, authoritativeRecoverableAmount,
                    authoritativeRevenueAtRisk, authoritativeRecoverableAmount,
                    strategy.getExpectedRecoveryPercentage(),
                    0.0, 0.0,
                    StoppingReason.NO_ACTION_REQUIRED,
                    "Passive strategy '" + strategyType + "' completed without synthetic retry invocations.",
                    true,
                    0, 0, 0,
                    SIMULATION_VERSION,
                    "{}"
            );
            passiveExecution = recoveryExecutionRepository.save(passiveExecution);
            auditEventService.recordRecoveryExecutionCompleted(passiveExecution);
            return mapToResponse(passiveExecution, List.of());
        }

        // 7. Select Eligible Synthetic Failed Payments
        List<Payment> eligiblePayments = selectEligiblePayments(incident);
        long eligibleCount = eligiblePayments.size();

        if (eligibleCount == 0) {
            RecoveryExecution emptyExecution = new RecoveryExecution(
                    incidentId,
                    strategy.getId(),
                    policyEvaluation.getId(),
                    startTime,
                    Instant.now(),
                    ExecutionStatus.COMPLETED,
                    strategyType,
                    0L, 0L, 0L, 0L, 0L,
                    BigDecimal.ZERO, BigDecimal.ZERO, authoritativeRecoverableAmount,
                    authoritativeRevenueAtRisk, authoritativeRecoverableAmount,
                    strategy.getExpectedRecoveryPercentage(),
                    0.0, 0.0,
                    StoppingReason.COMPLETED_ALL_ELIGIBLE,
                    "No eligible failed transactions located in incident window.",
                    true,
                    maxRetryAttempts, retryWindowMinutes, backoffBaseSeconds,
                    SIMULATION_VERSION,
                    "{}"
            );
            emptyExecution = recoveryExecutionRepository.save(emptyExecution);
            auditEventService.recordRecoveryExecutionCompleted(emptyExecution);
            return mapToResponse(emptyExecution, List.of());
        }

        // 8. Execute Bounded Synthetic Retry Simulation
        List<RecoveryExecutionAttempt> attemptEntities = new ArrayList<>();
        long attemptedCount = 0;
        long successfulCount = 0;
        long failedCount = 0;
        long skippedCount = 0;
        BigDecimal attemptedAmount = BigDecimal.ZERO;
        BigDecimal recoveredAmount = BigDecimal.ZERO;

        StoppingReason stoppingReason = StoppingReason.COMPLETED_ALL_ELIGIBLE;
        String stoppingDetails = "All eligible failed transactions processed within enforced retry policy bounds.";
        ExecutionStatus finalStatus = ExecutionStatus.COMPLETED;

        boolean circuitBreakerTripped = false;

        // Temporary execution ID placeholder until save
        RecoveryExecution executionEntity = new RecoveryExecution(
                incidentId,
                strategy.getId(),
                policyEvaluation.getId(),
                startTime,
                null,
                ExecutionStatus.RUNNING,
                strategyType,
                eligibleCount, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, authoritativeRecoverableAmount,
                authoritativeRevenueAtRisk, authoritativeRecoverableAmount,
                strategy.getExpectedRecoveryPercentage(),
                0.0, 0.0,
                StoppingReason.COMPLETED_ALL_ELIGIBLE,
                "In progress",
                true,
                maxRetryAttempts, retryWindowMinutes, backoffBaseSeconds,
                SIMULATION_VERSION,
                "{}"
        );
        executionEntity = recoveryExecutionRepository.save(executionEntity);
        auditEventService.recordRecoveryExecutionStarted(executionEntity);
        Long executionId = executionEntity.getId();

        for (Payment payment : eligiblePayments) {
            // Check Failure-Rate Circuit Breaker
            if (attemptedCount >= 5) {
                double currentFailureRate = ((double) failedCount / attemptedCount) * 100.0;
                if (currentFailureRate > circuitBreakerThreshold) {
                    circuitBreakerTripped = true;
                    stoppingReason = StoppingReason.CIRCUIT_BREAKER_FAILURE_RATE;
                    stoppingDetails = String.format("Circuit breaker triggered: execution failure rate (%.1f%%) exceeded threshold (%.1f%%). Halted further retry attempts.",
                            currentFailureRate, circuitBreakerThreshold);
                    finalStatus = ExecutionStatus.STOPPED;
                    break;
                }
            }

            attemptedCount++;
            attemptedAmount = attemptedAmount.add(payment.getAmount());
            boolean paymentRecovered = false;

            for (int attempt = 1; attempt <= maxRetryAttempts; attempt++) {
                int delaySeconds = (attempt == 1) ? 0 : (attempt - 1) * backoffBaseSeconds;
                if (delaySeconds > (retryWindowMinutes * 60)) {
                    RecoveryExecutionAttempt attemptRecord = new RecoveryExecutionAttempt(
                            executionId,
                            payment.getId(),
                            attempt,
                            startTime.plusSeconds(delaySeconds),
                            RecoveryOutcome.STOPPED_BY_LIMIT,
                            payment.getAmount(),
                            "Retry window of " + retryWindowMinutes + "m exceeded",
                            strategyType,
                            delaySeconds
                    );
                    attemptEntities.add(attemptRecord);
                    auditEventService.recordRecoveryAttempt(executionEntity, attemptRecord);
                    break;
                }

                boolean isSuccess = simulatePaymentRecoveryOutcome(incident, payment, attempt, strategyType);

                if (isSuccess) {
                    paymentRecovered = true;
                    successfulCount++;
                    recoveredAmount = recoveredAmount.add(payment.getAmount());

                    RecoveryExecutionAttempt attemptRecord = new RecoveryExecutionAttempt(
                            executionId,
                            payment.getId(),
                            attempt,
                            startTime.plusSeconds(delaySeconds),
                            RecoveryOutcome.RECOVERED,
                            payment.getAmount(),
                            null,
                            strategyType,
                            delaySeconds
                    );
                    attemptEntities.add(attemptRecord);
                    auditEventService.recordRecoveryAttempt(executionEntity, attemptRecord);
                    // Stop further retries for this payment on success
                    break;
                } else {
                    RecoveryExecutionAttempt attemptRecord = new RecoveryExecutionAttempt(
                            executionId,
                            payment.getId(),
                            attempt,
                            startTime.plusSeconds(delaySeconds),
                            RecoveryOutcome.FAILED,
                            payment.getAmount(),
                            "Simulated retry attempt " + attempt + " failed for error: " + payment.getErrorCode(),
                            strategyType,
                            delaySeconds
                    );
                    attemptEntities.add(attemptRecord);
                    auditEventService.recordRecoveryAttempt(executionEntity, attemptRecord);
                }
            }

            if (!paymentRecovered) {
                failedCount++;
            }
        }

        if (circuitBreakerTripped) {
            skippedCount = Math.max(0, eligibleCount - attemptedCount);
        }

        // 9. Enforce Authoritative Financial Cap
        if (recoveredAmount.compareTo(authoritativeRecoverableAmount) > 0) {
            recoveredAmount = authoritativeRecoverableAmount;
        }
        BigDecimal remainingRecoverable = authoritativeRecoverableAmount.subtract(recoveredAmount).max(BigDecimal.ZERO);

        double recoveryRate = eligibleCount > 0 ? ((double) successfulCount / eligibleCount) * 100.0 : 0.0;
        double financialRecoveryRate = authoritativeRecoverableAmount.compareTo(BigDecimal.ZERO) > 0
                ? recoveredAmount.divide(authoritativeRecoverableAmount, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue()
                : 0.0;

        // Persist attempts
        recoveryExecutionAttemptRepository.saveAll(attemptEntities);

        // Update & Persist final execution record
        Instant completedTime = Instant.now();
        RecoveryExecution finalized = new RecoveryExecution(
                incidentId,
                strategy.getId(),
                policyEvaluation.getId(),
                startTime,
                completedTime,
                finalStatus,
                strategyType,
                eligibleCount,
                attemptedCount,
                successfulCount,
                failedCount,
                skippedCount,
                recoveredAmount,
                attemptedAmount,
                remainingRecoverable,
                authoritativeRevenueAtRisk,
                authoritativeRecoverableAmount,
                strategy.getExpectedRecoveryPercentage(),
                recoveryRate,
                financialRecoveryRate,
                stoppingReason,
                stoppingDetails,
                true,
                maxRetryAttempts,
                retryWindowMinutes,
                backoffBaseSeconds,
                SIMULATION_VERSION,
                "{}"
        );

        // Replace running entity with finalized entity
        recoveryExecutionRepository.delete(executionEntity);
        finalized = recoveryExecutionRepository.save(finalized);
        auditEventService.recordRecoveryExecutionCompleted(finalized);

        return mapToResponse(finalized, attemptEntities);
    }

    @Transactional(readOnly = true)
    public Optional<RecoveryExecutionResponse> getLatestExecution(Long incidentId) {
        if (!incidentRepository.existsById(incidentId)) {
            throw new ResourceNotFoundException("Incident not found: " + incidentId);
        }

        return recoveryExecutionRepository.findFirstByIncidentIdOrderByStartedAtDesc(incidentId)
                .map(exec -> {
                    List<RecoveryExecutionAttempt> attempts = recoveryExecutionAttemptRepository
                            .findAllByExecutionIdOrderByAttemptNumberAsc(exec.getId());
                    return mapToResponse(exec, attempts);
                });
    }

    @Transactional(readOnly = true)
    public RecoveryExecutionResponse getExecutionById(Long executionId) {
        RecoveryExecution execution = recoveryExecutionRepository.findById(executionId)
                .orElseThrow(() -> new ResourceNotFoundException("Recovery execution not found: " + executionId));

        List<RecoveryExecutionAttempt> attempts = recoveryExecutionAttemptRepository
                .findAllByExecutionIdOrderByAttemptNumberAsc(execution.getId());
        return mapToResponse(execution, attempts);
    }

    /**
     * Deterministic simulation outcome calculation for a payment retry attempt.
     */
    private boolean simulatePaymentRecoveryOutcome(Incident incident, Payment payment, int attemptNumber, RecoveryStrategyType strategyType) {
        // Merchant degradation: retries against broken merchant endpoints do not succeed
        if (incident.getType() == IncidentType.MERCHANT_DEGRADATION) {
            return false;
        }

        ErrorCode errorCode = payment.getErrorCode();
        if (errorCode == null) {
            return true;
        }

        // Permanent non-recoverable errors
        if (errorCode == ErrorCode.INSUFFICIENT_FUNDS) {
            return false;
        }

        // Deterministic pseudo-random seed based on payment ID, attempt number, and incident ID
        long paymentId = payment.getId() != null ? payment.getId() : 1001L;
        long seed = (paymentId * 31L) + (attemptNumber * 17L) + (incident.getId() * 13L);
        double roll = (Math.abs(seed ^ (seed >>> 16)) % 1000) / 1000.0;

        // Base probability by error code
        double successThreshold;
        if (errorCode == ErrorCode.BANK_TIMEOUT || errorCode == ErrorCode.NETWORK_ERROR || errorCode == ErrorCode.RATE_LIMIT) {
            // Transient timeout / network / rate limit error: higher success on attempt 2 / 3
            successThreshold = 0.50 + (attemptNumber * 0.18);
        } else if (errorCode == ErrorCode.ISSUER_DECLINED) {
            // Issuer declined: medium recoverability
            successThreshold = 0.35 + (attemptNumber * 0.10);
        } else {
            successThreshold = 0.30;
        }

        return roll < Math.min(0.95, successThreshold);
    }

    /**
     * Selects genuine affected synthetic failed payments for the incident batch.
     */
    private List<Payment> selectEligiblePayments(Incident incident) {
        Specification<Payment> spec = Specification.where(PaymentSpecifications.hasStatus(PaymentStatus.FAILED));

        if (incident.getTargetBank() != null && !incident.getTargetBank().isBlank()) {
            spec = spec.and(PaymentSpecifications.hasBank(incident.getTargetBank()));
        }
        if (incident.getTargetPaymentMethod() != null) {
            spec = spec.and(PaymentSpecifications.hasPaymentMethod(incident.getTargetPaymentMethod()));
        }
        if (incident.getTargetMerchantId() != null && !incident.getTargetMerchantId().isBlank()) {
            spec = spec.and(PaymentSpecifications.hasMerchantId(incident.getTargetMerchantId()));
        }
        if (incident.getStartTime() != null) {
            spec = spec.and(PaymentSpecifications.timestampFrom(incident.getStartTime()));
        }

        return paymentRepository.findAll(spec);
    }

    private RecoveryExecutionResponse createBlockedExecution(Long incidentId,
                                                             Long strategyId,
                                                             Long policyEvaluationId,
                                                             RecoveryStrategyType strategyType,
                                                             BigDecimal authoritativeRevenueAtRisk,
                                                             BigDecimal authoritativeRecoverableAmount,
                                                             Double expectedRecoveryPercentage,
                                                             StoppingReason stoppingReason,
                                                             String stoppingDetails,
                                                             Instant startTime) {
        RecoveryExecution blocked = new RecoveryExecution(
                incidentId,
                strategyId,
                policyEvaluationId,
                startTime,
                Instant.now(),
                ExecutionStatus.BLOCKED,
                strategyType,
                0L, 0L, 0L, 0L, 0L,
                BigDecimal.ZERO, BigDecimal.ZERO, authoritativeRecoverableAmount,
                authoritativeRevenueAtRisk, authoritativeRecoverableAmount,
                expectedRecoveryPercentage,
                0.0, 0.0,
                stoppingReason,
                stoppingDetails,
                false,
                0, 0, 0,
                SIMULATION_VERSION,
                "{}"
        );
        blocked = recoveryExecutionRepository.save(blocked);
        auditEventService.recordRecoveryExecutionBlocked(incidentId, strategyId, policyEvaluationId, stoppingReason, stoppingDetails);
        return mapToResponse(blocked, List.of());
    }

    private RecoveryExecutionResponse mapToResponse(RecoveryExecution entity, List<RecoveryExecutionAttempt> attempts) {
        List<RecoveryExecutionAttemptDto> attemptDtos = attempts.stream()
                .map(a -> new RecoveryExecutionAttemptDto(
                        a.getId(),
                        a.getPaymentId(),
                        a.getAttemptNumber(),
                        a.getAttemptedAt(),
                        a.getOutcome(),
                        a.getAmount(),
                        a.getFailureReason(),
                        a.getStrategyType(),
                        a.getSimulatedDelaySeconds()
                ))
                .toList();

        long durationSeconds = entity.getCompletedAt() != null
                ? Duration.between(entity.getStartedAt(), entity.getCompletedAt()).toSeconds()
                : 0L;

        double avgAttempts = entity.getSuccessfulRecoveries() > 0
                ? (double) entity.getAttemptedTransactions() / entity.getSuccessfulRecoveries()
                : 0.0;

        RecoveryMetricsDto metrics = new RecoveryMetricsDto(
                entity.getRecoveryRate(),
                entity.getFinancialRecoveryRate(),
                avgAttempts,
                durationSeconds
        );

        return new RecoveryExecutionResponse(
                entity.getId(),
                entity.getIncidentId(),
                entity.getStrategyId(),
                entity.getPolicyEvaluationId(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getStrategyType(),
                entity.getEligibleTransactions(),
                entity.getAttemptedTransactions(),
                entity.getSuccessfulRecoveries(),
                entity.getFailedRecoveries(),
                entity.getSkippedTransactions(),
                entity.getRecoveredAmount(),
                entity.getAttemptedAmount(),
                entity.getRemainingRecoverableAmount(),
                entity.getAuthoritativeRevenueAtRisk(),
                entity.getAuthoritativeRecoverableAmount(),
                entity.getExpectedRecoveryPercentage(),
                metrics,
                entity.getStoppingReason(),
                entity.getStoppingDetails(),
                Boolean.TRUE.equals(entity.getExecutionAllowed()),
                entity.getAppliedRetryLimit(),
                entity.getAppliedRetryWindow(),
                entity.getAppliedBackoff(),
                entity.getSimulationVersion(),
                attemptDtos,
                RecoveryExecutionResponse.DEFAULT_SIMULATION_DISCLAIMER
        );
    }
}
