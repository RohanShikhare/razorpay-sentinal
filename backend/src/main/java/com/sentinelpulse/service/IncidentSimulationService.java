package com.sentinelpulse.service;

import com.sentinelpulse.dto.SimulationResetResponse;
import com.sentinelpulse.dto.SimulationRunResponse;
import com.sentinelpulse.dto.SimulationStatusResponse;
import com.sentinelpulse.entity.Banks;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.entity.SimulationChange;
import com.sentinelpulse.entity.SimulationRun;
import com.sentinelpulse.entity.SimulationScenario;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PaymentSpecifications;
import com.sentinelpulse.repository.SimulationChangeRepository;
import com.sentinelpulse.repository.SimulationRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;

/**
 * Deterministically manipulates the synthetic payment dataset to create
 * controlled, reproducible payment-degradation scenarios.
 *
 * This service creates incidents in the data. It never declares that an
 * incident has been detected - that judgment is the Monitoring &
 * Incident Detection Engine's job, which will read the same
 * {@code payments} table this service mutates. To keep that boundary
 * clean, nothing here writes to an "Incident" entity; {@link SimulationRun}
 * and {@link SimulationChange} are explicitly simulation records, not
 * detected incidents.
 */
@Service
public class IncidentSimulationService {

    private static final double BANK_X_UPI_TARGET_FAILURE_RATE = 0.32;
    private static final double MERCHANT_TARGET_FAILURE_RATE = 0.30;
    private static final double NORMAL_NOISE_INCREASE = 0.03;
    private static final double NORMAL_NOISE_CAP = 0.10;
    private static final long MIN_MERCHANT_VOLUME_FOR_TARGETING = 10;

    private final PaymentRepository paymentRepository;
    private final SimulationRunRepository simulationRunRepository;
    private final SimulationChangeRepository simulationChangeRepository;

    public IncidentSimulationService(PaymentRepository paymentRepository,
                                      SimulationRunRepository simulationRunRepository,
                                      SimulationChangeRepository simulationChangeRepository) {
        this.paymentRepository = paymentRepository;
        this.simulationRunRepository = simulationRunRepository;
        this.simulationChangeRepository = simulationChangeRepository;
    }

    @Transactional
    public SimulationRunResponse runScenario(SimulationScenario scenario) {
        Optional<SimulationRun> active = simulationRunRepository.findFirstByActiveTrueOrderByStartedAtDesc();
        boolean previousReset = active.isPresent();
        active.ifPresent(this::restore);

        ScenarioOutcome outcome = switch (scenario) {
            case BANK_X_UPI_DEGRADATION -> runBankXUpiDegradation();
            case MERCHANT_SPECIFIC_DEGRADATION -> runMerchantSpecificDegradation();
            case NORMAL_RANDOM_FAILURES -> runNormalRandomFailures();
        };

        Instant now = Instant.now();
        SimulationRun run = new SimulationRun(
                scenario, now, outcome.targetBank(), outcome.targetPaymentMethod(), outcome.targetMerchantId(),
                outcome.affectedTransactions(), outcome.modifiedTransactions(),
                outcome.failureRateBefore(), outcome.failureRateAfter()
        );
        run = simulationRunRepository.save(run);

        if (!outcome.mutatedPayments().isEmpty()) {
            paymentRepository.saveAll(outcome.mutatedPayments());

            Long runId = run.getId();
            List<SimulationChange> changes = outcome.changes().stream()
                    .map(c -> new SimulationChange(runId, c.paymentId(), c.previousStatus(), c.previousErrorCode()))
                    .toList();
            simulationChangeRepository.saveAll(changes);
        }

        return new SimulationRunResponse(
                scenario, outcome.affectedTransactions(), outcome.modifiedTransactions(),
                outcome.targetBank(), outcome.targetPaymentMethod(), outcome.targetMerchantId(),
                outcome.failureRateBefore(), outcome.failureRateAfter(), now,
                previousReset, outcome.message()
        );
    }

    @Transactional
    public SimulationResetResponse reset() {
        Optional<SimulationRun> active = simulationRunRepository.findFirstByActiveTrueOrderByStartedAtDesc();
        if (active.isEmpty()) {
            return new SimulationResetResponse(false, 0, Instant.now(), "No active simulation to reset.");
        }
        long restored = restore(active.get());
        return new SimulationResetResponse(true, restored, Instant.now(), null);
    }

    @Transactional(readOnly = true)
    public SimulationStatusResponse getStatus() {
        return simulationRunRepository.findFirstByActiveTrueOrderByStartedAtDesc()
                .map(run -> new SimulationStatusResponse(
                        true, run.getScenario(), run.getStartedAt(),
                        run.getAffectedTransactions(), run.getModifiedTransactions(),
                        run.getTargetBank(), run.getTargetPaymentMethod(), run.getTargetMerchantId(),
                        run.getFailureRateBefore(), run.getFailureRateAfter()
                ))
                .orElseGet(() -> new SimulationStatusResponse(
                        false, null, null, 0, 0, null, null, null, 0, 0
                ));
    }

    // ---------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------

    /**
     * Restores every payment a run touched to its pre-simulation
     * status/error code, deletes the change records, and marks the run
     * inactive. Returns how many payments were restored.
     */
    private long restore(SimulationRun run) {
        List<SimulationChange> changes = simulationChangeRepository.findBySimulationRunId(run.getId());
        if (!changes.isEmpty()) {
            List<Long> paymentIds = changes.stream().map(SimulationChange::getPaymentId).toList();
            Map<Long, Payment> paymentsById = paymentRepository.findAllById(paymentIds).stream()
                    .collect(Collectors.toMap(Payment::getId, p -> p));

            for (SimulationChange change : changes) {
                Payment payment = paymentsById.get(change.getPaymentId());
                if (payment != null) {
                    payment.setStatus(change.getPreviousStatus());
                    payment.setErrorCode(change.getPreviousErrorCode());
                }
            }
            paymentRepository.saveAll(paymentsById.values());
            simulationChangeRepository.deleteAll(changes);
        }

        run.markEnded(Instant.now());
        simulationRunRepository.save(run);
        return changes.size();
    }

    // ---------------------------------------------------------------
    // Scenario A: Bank X UPI Degradation
    // ---------------------------------------------------------------

    private ScenarioOutcome runBankXUpiDegradation() {
        List<Payment> segment = paymentRepository.findAll(
                PaymentSpecifications.filter(null, PaymentMethod.UPI, Banks.BANK_X, null, null, null));

        if (segment.isEmpty()) {
            return ScenarioOutcome.empty(Banks.BANK_X, PaymentMethod.UPI, null,
                    "No Bank X + UPI transactions found. Generate payment data first.");
        }

        Random random = new Random(SimulationScenario.BANK_X_UPI_DEGRADATION.name().hashCode());
        return degradeSegment(segment, BANK_X_UPI_TARGET_FAILURE_RATE, random,
                errorCodeDistributionDominatedByBankTimeout(),
                Banks.BANK_X, PaymentMethod.UPI, null, true);
    }

    // ---------------------------------------------------------------
    // Scenario B: Merchant-specific degradation
    // ---------------------------------------------------------------

    private ScenarioOutcome runMerchantSpecificDegradation() {
        List<Object[]> ranking = paymentRepository.merchantVolumeRanking();
        Optional<String> target = ranking.stream()
                .filter(row -> ((Long) row[1]) >= MIN_MERCHANT_VOLUME_FOR_TARGETING)
                .map(row -> (String) row[0])
                .findFirst();

        if (target.isEmpty()) {
            return ScenarioOutcome.empty(null, null, null,
                    "No merchant with enough transaction volume found. Generate payment data first.");
        }

        String merchantId = target.get();
        List<Payment> segment = paymentRepository.findAll(
                PaymentSpecifications.hasMerchantId(merchantId));

        Random random = new Random((SimulationScenario.MERCHANT_SPECIFIC_DEGRADATION.name() + merchantId).hashCode());
        return degradeSegment(segment, MERCHANT_TARGET_FAILURE_RATE, random,
                errorCodeDistributionWithoutBankBlame(),
                null, null, merchantId, true);
    }

    // ---------------------------------------------------------------
    // Scenario C: Normal random failures
    // ---------------------------------------------------------------

    private ScenarioOutcome runNormalRandomFailures() {
        List<Payment> segment = paymentRepository.findAll();

        if (segment.isEmpty()) {
            return ScenarioOutcome.empty(null, null, null,
                    "No transactions found. Generate payment data first.");
        }

        long alreadyFailed = segment.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        double currentRate = rate(alreadyFailed, segment.size());
        double target = Math.min(currentRate + NORMAL_NOISE_INCREASE, NORMAL_NOISE_CAP);

        Random random = new Random(SimulationScenario.NORMAL_RANDOM_FAILURES.name().hashCode());
        return degradeSegment(segment, target, random,
                generalErrorCodeDistribution(),
                null, null, null, false);
    }

    // ---------------------------------------------------------------
    // Shared degradation logic
    // ---------------------------------------------------------------

    /**
     * Raises the failure rate of {@code segment} toward {@code targetRate}
     * (a fraction, e.g. 0.32 for 32%) by converting a subset of its
     * SUCCESS/PENDING payments to FAILED, assigning error codes from
     * {@code errorCodeWeights}. Amount, customer, merchant, and timestamp
     * are left untouched - only status and error code change.
     *
     * When {@code concentrateInRecentTraffic} is true, the most recent
     * eligible payments are chosen first - a real incident degrades recent
     * traffic, not transactions from days ago, and this is also what makes
     * the degradation visible to a time-window-based detector
     * comparing a recent window against an older baseline. When false (the
     * "normal random failures" control scenario), the affected payments are
     * spread uniformly across the whole timeline instead, representing
     * constant background noise rather than a new, detectable event.
     */
    private ScenarioOutcome degradeSegment(List<Payment> segment, double targetRate, Random random,
                                            List<Weighted<ErrorCode>> errorCodeWeights,
                                            String targetBank, PaymentMethod targetPaymentMethod,
                                            String targetMerchantId, boolean concentrateInRecentTraffic) {
        long total = segment.size();
        long alreadyFailed = segment.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).count();
        double before = rate(alreadyFailed, total);

        List<Payment> eligible = segment.stream()
                .filter(p -> p.getStatus() != PaymentStatus.FAILED)
                .collect(Collectors.toCollection(ArrayList::new));

        if (concentrateInRecentTraffic) {
            eligible.sort(Comparator.comparing(Payment::getTimestamp).reversed());
        } else {
            Collections.shuffle(eligible, random);
        }

        long targetFailedCount = (long) Math.ceil(targetRate * total);
        long needed = Math.max(0, targetFailedCount - alreadyFailed);
        long modifyCount = Math.min(needed, eligible.size());

        List<Payment> toModify = eligible.subList(0, (int) modifyCount);
        List<PendingChange> changes = new ArrayList<>();
        List<Payment> mutated = new ArrayList<>();

        for (Payment payment : toModify) {
            changes.add(new PendingChange(payment.getId(), payment.getStatus(), payment.getErrorCode()));
            payment.setStatus(PaymentStatus.FAILED);
            payment.setErrorCode(pickWeighted(random, errorCodeWeights));
            mutated.add(payment);
        }

        double after = rate(alreadyFailed + modifyCount, total);

        return new ScenarioOutcome(total, modifyCount, targetBank, targetPaymentMethod, targetMerchantId,
                before, after, mutated, changes, null);
    }

    private static List<Weighted<ErrorCode>> errorCodeDistributionDominatedByBankTimeout() {
        return List.of(
                new Weighted<>(ErrorCode.BANK_TIMEOUT, 0.80),
                new Weighted<>(ErrorCode.NETWORK_ERROR, 0.12),
                new Weighted<>(ErrorCode.UNKNOWN, 0.08)
        );
    }

    private static List<Weighted<ErrorCode>> errorCodeDistributionWithoutBankBlame() {
        return List.of(
                new Weighted<>(ErrorCode.ISSUER_DECLINED, 0.40),
                new Weighted<>(ErrorCode.INSUFFICIENT_FUNDS, 0.35),
                new Weighted<>(ErrorCode.UNKNOWN, 0.15),
                new Weighted<>(ErrorCode.RATE_LIMIT, 0.10)
        );
    }

    private static List<Weighted<ErrorCode>> generalErrorCodeDistribution() {
        return List.of(
                new Weighted<>(ErrorCode.BANK_TIMEOUT, 0.30),
                new Weighted<>(ErrorCode.ISSUER_DECLINED, 0.25),
                new Weighted<>(ErrorCode.INSUFFICIENT_FUNDS, 0.20),
                new Weighted<>(ErrorCode.NETWORK_ERROR, 0.15),
                new Weighted<>(ErrorCode.RATE_LIMIT, 0.07),
                new Weighted<>(ErrorCode.UNKNOWN, 0.03)
        );
    }

    private static <T> T pickWeighted(Random random, List<Weighted<T>> weights) {
        double roll = random.nextDouble();
        double cumulative = 0.0;
        for (Weighted<T> entry : weights) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return entry.value();
            }
        }
        return weights.get(weights.size() - 1).value();
    }

    private static double rate(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part)
                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private record Weighted<T>(T value, double weight) {
    }

    private record PendingChange(Long paymentId, PaymentStatus previousStatus, ErrorCode previousErrorCode) {
    }

    private record ScenarioOutcome(
            long affectedTransactions,
            long modifiedTransactions,
            String targetBank,
            PaymentMethod targetPaymentMethod,
            String targetMerchantId,
            double failureRateBefore,
            double failureRateAfter,
            List<Payment> mutatedPayments,
            List<PendingChange> changes,
            String message
    ) {
        static ScenarioOutcome empty(String targetBank, PaymentMethod targetPaymentMethod,
                                      String targetMerchantId, String message) {
            return new ScenarioOutcome(0, 0, targetBank, targetPaymentMethod, targetMerchantId,
                    0.0, 0.0, List.of(), List.of(), message);
        }
    }
}
