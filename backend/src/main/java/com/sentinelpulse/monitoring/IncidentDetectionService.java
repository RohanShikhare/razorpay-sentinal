package com.sentinelpulse.monitoring;

import com.sentinelpulse.config.MonitoringProperties;
import com.sentinelpulse.config.MonitoringProperties.Thresholds;
import com.sentinelpulse.dto.DetectionResponse;
import com.sentinelpulse.dto.IncidentDetailResponse;
import com.sentinelpulse.dto.IncidentDto;
import com.sentinelpulse.dto.MonitoringSummaryResponse;
import com.sentinelpulse.dto.SegmentBreakdownDto;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.monitoring.PaymentStatistics.SegmentStats;
import com.sentinelpulse.monitoring.SegmentAnalyzer.SegmentSignal;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PaymentSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * The deterministic Monitoring & Incident Detection Engine.
 *
 * <h2>Core flow</h2>
 * Payment data → baseline statistics → current-window statistics → compare
 * → threshold evaluation → incident detected / not detected.
 *
 * <h2>Baseline</h2>
 * The baseline is every payment older than the current monitoring window -
 * i.e. "everything that isn't the recent slice we're evaluating". With the
 * payment generator's default 48-hour spread, this is typically the large
 * majority of the dataset, which keeps the baseline statistically stable.
 *
 * <h2>Current window</h2>
 * Payments with a timestamp inside the last {@code windowMinutes}. If that
 * window contains too few transactions to be statistically meaningful
 * ({@link MonitoringProperties#getMinimumSampleSize()}), the window is
 * doubled (up to {@link MonitoringProperties#getMaxWindowMinutes()}) until
 * it does. This is a genuine statistical safeguard, not a demo shortcut -
 * a handful of transactions in an arbitrarily narrow window would produce
 * meaningless swings either way. The effective window used is always
 * reported in the response.
 *
 * <h2>Segment detection</h2>
 * The engine never looks only at the global failure rate. It also compares
 * baseline vs current for every bank, payment method, bank+payment-method
 * combination, and merchant with enough volume to trust ({@link
 * SegmentAnalyzer}), so a segment-concentrated issue (Bank X + UPI) is
 * caught even when the system-wide rate barely moves.
 *
 * <h2>Severity</h2>
 * Driven by whichever is larger: the global failure-rate increase (in
 * percentage points) or the most-deviated qualifying segment's increase,
 * compared against {@link Thresholds}.
 *
 * <h2>Revenue at risk</h2>
 * {@code revenueAtRisk = failedTransactionValue × max(0, (currentFailureRate − baselineFailureRate) / currentFailureRate)}
 * — i.e. the share of currently-failed value attributable to the *excess*
 * failure rate over baseline, not all failures (baseline already has some
 * "normal" failures that aren't the incident's doing).
 *
 * <h2>Confidence</h2>
 * A 0-100 deterministic score - not an AI confidence score - combining
 * three signals, each capped and weighted: deviation magnitude relative to
 * the critical threshold (up to 40 points), affected-transaction sample
 * size relative to a reference size (up to 30 points), and how much more
 * concentrated the top segment is versus the global rate (up to 30 points).
 * See {@link #computeConfidence}.
 *
 * <h2>What this engine does not do</h2>
 * It never calls an LLM, never assigns {@code rootCause}, never recommends
 * or executes recovery. Root-cause diagnosis is a future AI investigation
 * module's job; this engine only ever states what it statistically observed.
 */
@Service
public class IncidentDetectionService {

    /** How many top segments to include in API responses. */
    private static final int TOP_SEGMENTS_LIMIT = 8;

    /** Affected-transaction count that earns the full 30 confidence points. */
    private static final double CONFIDENCE_SAMPLE_SIZE_REFERENCE = 200.0;

    /** Segment-vs-global concentration gap (percentage points) for the full 30 confidence points. */
    private static final double CONFIDENCE_CONCENTRATION_REFERENCE = 20.0;

    private final PaymentRepository paymentRepository;
    private final IncidentRepository incidentRepository;
    private final MonitoringProperties properties;
    private final FinancialImpactService financialImpactService;

    public IncidentDetectionService(PaymentRepository paymentRepository, IncidentRepository incidentRepository,
                                     MonitoringProperties properties, FinancialImpactService financialImpactService) {
        this.paymentRepository = paymentRepository;
        this.incidentRepository = incidentRepository;
        this.properties = properties;
        this.financialImpactService = financialImpactService;
    }

    @Transactional
    public DetectionResponse detect(Integer requestedWindowMinutes) {
        Instant now = Instant.now();
        Snapshot snapshot = computeSnapshot(requestedWindowMinutes, now);

        if (snapshot.currentPayments().isEmpty() || snapshot.baselinePayments().isEmpty()) {
            return notDetected(snapshot, "Not enough payment data to evaluate. Generate payment data first.");
        }

        List<SegmentSignal> segmentSignals = SegmentAnalyzer.evaluate(
                snapshot.baselinePayments(), snapshot.currentPayments(), properties.getMinimumSegmentSampleSize());

        List<SegmentSignal> actionableSignals = segmentSignals.stream()
                .filter(s -> s.current().failed() >= 3)
                .toList();

        SegmentSignal topSegment = actionableSignals.isEmpty() ? null : actionableSignals.get(0);
        double topSegmentIncrease = topSegment != null ? topSegment.failureRateIncrease() : Double.NEGATIVE_INFINITY;
        double maxIncrease = Math.max(snapshot.globalIncrease(), topSegmentIncrease);

        Thresholds thresholds = properties.getThresholds();
        if (maxIncrease < thresholds.getMinimumIncreasePoints()) {
            return notDetected(snapshot, String.format(
                    "Failure rate increase of %.2f points is within normal variation (threshold: %.2f points).",
                    maxIncrease, thresholds.getMinimumIncreasePoints()));
        }

        boolean segmentDriven = topSegment != null && topSegmentIncrease >= snapshot.globalIncrease();

        IncidentType type;
        String targetBank = null;
        PaymentMethod targetMethod = null;
        String targetMerchant = null;
        SegmentStats affectedBaseline;
        SegmentStats affectedCurrent;

        if (segmentDriven) {
            affectedBaseline = topSegment.baseline();
            affectedCurrent = topSegment.current();
            if (topSegment.dimension() == SegmentAnalyzer.Dimension.MERCHANT) {
                type = IncidentType.MERCHANT_DEGRADATION;
                targetMerchant = topSegment.merchantId();
            } else {
                type = IncidentType.PAYMENT_DEGRADATION;
                targetBank = topSegment.bank();
                targetMethod = topSegment.paymentMethod();
            }
        } else {
            type = IncidentType.SYSTEM_DEGRADATION;
            affectedBaseline = snapshot.baselineGlobal();
            affectedCurrent = snapshot.currentGlobal();
        }

        double increase = maxIncrease;
        IncidentSeverity severity = severityFor(increase, thresholds, affectedCurrent);
        BigDecimal revenueAtRisk = calculateRevenueAtRisk(affectedBaseline, affectedCurrent);
        double concentrationGap = segmentDriven ? Math.max(0, topSegmentIncrease - snapshot.globalIncrease()) : 0.0;
        double confidence = computeConfidence(increase, affectedCurrent.total(), concentrationGap, thresholds);
        Double relativeIncrease = affectedBaseline.failureRate() > 0
                ? round2((affectedCurrent.failureRate() - affectedBaseline.failureRate()) / affectedBaseline.failureRate() * 100.0)
                : null;

        Incident saved = persistOrRefreshIncident(type, severity, now, snapshot.windowStart(), affectedCurrent,
                revenueAtRisk, confidence, affectedBaseline.failureRate(), affectedCurrent.failureRate(), increase,
                targetBank, targetMethod, targetMerchant, snapshot.windowMinutes());

        List<SegmentBreakdownDto> topSegments = toBreakdownDtos(segmentSignals);

        return new DetectionResponse(
                true, saved.getId(), type, severity,
                affectedBaseline.failureRate(), affectedCurrent.failureRate(), increase, relativeIncrease,
                affectedCurrent.total(), affectedCurrent.totalValue(), affectedCurrent.failedValue(), revenueAtRisk,
                confidence, snapshot.windowMinutes(), targetBank, targetMethod, targetMerchant,
                topSegments, now, null
        );
    }

    @Transactional(readOnly = true)
    public MonitoringSummaryResponse getSummary(Integer requestedWindowMinutes) {
        Snapshot snapshot = computeSnapshot(requestedWindowMinutes, Instant.now());
        List<Incident> active = incidentRepository.findByStatusNot(IncidentStatus.RESOLVED);

        BigDecimal totalRevenueAtRisk = active.stream()
                .map(Incident::getRevenueAtRisk)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAffectedValue = active.stream()
                .map(Incident::getAffectedTransactionValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalRecoverable = active.stream()
                .map(i -> financialImpactService.getFinancialImpact(i.getId()).estimatedRecoverableRevenue())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        long totalAffected = active.stream().mapToLong(Incident::getAffectedTransactions).sum();
        String health = active.stream()
                .map(Incident::getSeverity)
                .max(Comparator.comparingInt(IncidentSeverity::ordinal))
                .map(Enum::name)
                .orElse("NORMAL");

        return new MonitoringSummaryResponse(
                health, snapshot.currentGlobal().successRate(), snapshot.currentGlobal().failureRate(),
                snapshot.baselineGlobal().failureRate(), snapshot.globalIncrease(),
                totalRevenueAtRisk, totalRecoverable, totalAffectedValue, active.size(), totalAffected
        );
    }

    @Transactional(readOnly = true)
    public Page<IncidentDto> listIncidents(Pageable pageable) {
        return incidentRepository.findAllByOrderByDetectedAtDesc(pageable).map(IncidentDto::from);
    }

    @Transactional(readOnly = true)
    public IncidentDetailResponse getIncidentDetail(Long id) {
        Incident incident = incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));

        List<Payment> baseline = paymentRepository.findAll(PaymentSpecifications.timestampTo(incident.getStartTime()));
        List<Payment> current = paymentRepository.findAll(PaymentSpecifications.timestampFrom(incident.getStartTime()));
        List<SegmentSignal> signals = SegmentAnalyzer.evaluate(baseline, current, properties.getMinimumSegmentSampleSize());

        return new IncidentDetailResponse(IncidentDto.from(incident), toBreakdownDtos(signals));
    }

    // ---------------------------------------------------------------
    // Snapshot: baseline + current statistics for a given window
    // ---------------------------------------------------------------

    private record Snapshot(
            int windowMinutes, Instant windowStart,
            List<Payment> baselinePayments, List<Payment> currentPayments,
            SegmentStats baselineGlobal, SegmentStats currentGlobal, double globalIncrease
    ) {
    }

    private Snapshot computeSnapshot(Integer requestedWindowMinutes, Instant now) {
        int windowMinutes = requestedWindowMinutes != null ? requestedWindowMinutes : properties.getDefaultWindowMinutes();

        Instant windowStart;
        List<Payment> currentPayments;
        while (true) {
            windowStart = now.minus(windowMinutes, ChronoUnit.MINUTES);
            currentPayments = paymentRepository.findAll(PaymentSpecifications.timestampFrom(windowStart));
            boolean wideEnough = currentPayments.size() >= properties.getMinimumSampleSize();
            boolean atCap = windowMinutes >= properties.getMaxWindowMinutes();
            if (wideEnough || atCap) {
                break;
            }
            windowMinutes = Math.min(windowMinutes * 2, properties.getMaxWindowMinutes());
        }

        List<Payment> baselinePayments = paymentRepository.findAll(PaymentSpecifications.timestampTo(windowStart));
        SegmentStats baselineGlobal = PaymentStatistics.compute(baselinePayments);
        SegmentStats currentGlobal = PaymentStatistics.compute(currentPayments);
        double globalIncrease = currentGlobal.failureRate() - baselineGlobal.failureRate();

        return new Snapshot(windowMinutes, windowStart, baselinePayments, currentPayments,
                baselineGlobal, currentGlobal, globalIncrease);
    }

    // ---------------------------------------------------------------
    // Severity, revenue at risk, confidence
    // ---------------------------------------------------------------

    private IncidentSeverity severityFor(double increase, Thresholds thresholds, SegmentStats current) {
        IncidentSeverity raw;
        if (increase >= thresholds.getCriticalIncreasePoints()) {
            raw = IncidentSeverity.CRITICAL;
        } else if (increase >= thresholds.getHighIncreasePoints()) {
            raw = IncidentSeverity.HIGH;
        } else if (increase >= thresholds.getMediumIncreasePoints()) {
            raw = IncidentSeverity.MEDIUM;
        } else {
            raw = IncidentSeverity.LOW;
        }

        // On small samples, guard against false alarms from single/double random failures
        if (raw == IncidentSeverity.CRITICAL || raw == IncidentSeverity.HIGH) {
            if (current != null && current.failed() < 3) {
                return IncidentSeverity.MEDIUM;
            }
        }
        if (raw == IncidentSeverity.CRITICAL) {
            if (current != null && (current.total() < 15 && current.failed() < 6)) {
                return IncidentSeverity.HIGH;
            }
        }
        return raw;
    }

    /**
     * revenueAtRisk = failedTransactionValue × max(0, (currentRate − baselineRate) / currentRate)
     * — see the class-level Javadoc for the reasoning.
     */
    private BigDecimal calculateRevenueAtRisk(SegmentStats baseline, SegmentStats current) {
        if (current.failureRate() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        double proportion = Math.max(0.0, (current.failureRate() - baseline.failureRate()) / current.failureRate());
        return current.failedValue()
                .multiply(BigDecimal.valueOf(proportion))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private double computeConfidence(double increase, long affectedTransactions, double concentrationGap,
                                      Thresholds thresholds) {
        double magnitude = 40.0 * Math.min(1.0, increase / thresholds.getCriticalIncreasePoints());
        double sampleSize = 30.0 * Math.min(1.0, affectedTransactions / CONFIDENCE_SAMPLE_SIZE_REFERENCE);
        double concentration = 30.0 * Math.min(1.0, concentrationGap / CONFIDENCE_CONCENTRATION_REFERENCE);
        double total = magnitude + sampleSize + concentration;
        return round2(Math.max(0.0, Math.min(100.0, total)));
    }

    // ---------------------------------------------------------------
    // Persistence / duplicate suppression
    // ---------------------------------------------------------------

    /**
     * If an active (non-RESOLVED) incident already exists for the same
     * underlying condition (same type + same target bank/method/merchant),
     * refreshes it with the latest reading instead of creating a duplicate.
     */
    private Incident persistOrRefreshIncident(IncidentType type, IncidentSeverity severity, Instant now,
                                               Instant startTime, SegmentStats affectedCurrent,
                                               BigDecimal revenueAtRisk, double confidence,
                                               double baselineFailureRate, double currentFailureRate, double increase,
                                               String targetBank, PaymentMethod targetMethod, String targetMerchant,
                                               int windowMinutes) {

        Incident existing = incidentRepository.findByStatusNot(IncidentStatus.RESOLVED).stream()
                .filter(i -> i.getType() == type)
                .filter(i -> Objects.equals(i.getTargetBank(), targetBank))
                .filter(i -> i.getTargetPaymentMethod() == targetMethod)
                .filter(i -> Objects.equals(i.getTargetMerchantId(), targetMerchant))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.refresh(severity, now, affectedCurrent.total(), affectedCurrent.totalValue(),
                    affectedCurrent.failedValue(), revenueAtRisk, confidence,
                    baselineFailureRate, currentFailureRate, increase);
            return incidentRepository.save(existing);
        }

        Incident incident = new Incident(type, severity, now, startTime, affectedCurrent.total(),
                affectedCurrent.totalValue(), affectedCurrent.failedValue(), revenueAtRisk, confidence,
                IncidentStatus.DETECTED, baselineFailureRate, currentFailureRate, increase,
                targetBank, targetMethod, targetMerchant, windowMinutes);
        return incidentRepository.save(incident);
    }

    // ---------------------------------------------------------------
    // Response helpers
    // ---------------------------------------------------------------

    private DetectionResponse notDetected(Snapshot snapshot, String message) {
        return new DetectionResponse(
                false, null, null, null,
                snapshot.baselineGlobal().failureRate(), snapshot.currentGlobal().failureRate(),
                snapshot.globalIncrease(), null,
                snapshot.currentGlobal().total(), snapshot.currentGlobal().totalValue(),
                snapshot.currentGlobal().failedValue(), BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                null, snapshot.windowMinutes(), null, null, null,
                List.of(), Instant.now(), message
        );
    }

    private List<SegmentBreakdownDto> toBreakdownDtos(List<SegmentSignal> signals) {
        return signals.stream()
                .limit(TOP_SEGMENTS_LIMIT)
                .map(s -> new SegmentBreakdownDto(
                        s.dimension().name(), s.label(),
                        s.baseline().total(), s.current().total(),
                        s.baseline().failureRate(), s.current().failureRate(), s.failureRateIncrease()
                ))
                .toList();
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
