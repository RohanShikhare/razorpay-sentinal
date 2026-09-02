package com.sentinelpulse.financial;

import com.sentinelpulse.config.RecoverabilityProperties;
import com.sentinelpulse.dto.FinancialBreakdownResponse;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.dto.FinancialSegmentDto;
import com.sentinelpulse.dto.IncidentEvidenceDto;
import com.sentinelpulse.dto.IncidentEvidenceDto.SegmentFinancialsDto;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PaymentSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Deterministic financial-impact and recoverability engine, built on top of
 * the revenue-at-risk figure Module 3's {@code IncidentDetectionService}
 * already computes and persists on each {@link Incident}.
 *
 * <h2>Why revenueAtRisk is read, not recomputed</h2>
 * {@link Incident#getRevenueAtRisk()} is a value captured at (re)detection
 * time. This service treats it as the historical snapshot of "how much
 * revenue was at risk when this was detected" and never overwrites it -
 * only {@code IncidentDetectionService.refresh} (a genuine re-detection
 * event) updates it. That's what keeps the top-line financial-impact
 * numbers explainable even if the underlying payment data changes later
 * (e.g. a future recovery module, or a simulation reset).
 *
 * <h2>Affected batch reconstruction</h2>
 * No transactions are duplicated into a second table. The population is
 * reconstructed on demand from the existing {@code payments} table using
 * the incident's own deterministic criteria: {@code targetBank} /
 * {@code targetPaymentMethod} / {@code targetMerchantId} (whichever apply
 * to the incident's type) plus every payment from {@code startTime} onward.
 * Segment breakdowns and the recoverability estimate are computed live from
 * this batch each time - so, unlike the top-line revenue-at-risk, they
 * reflect the current state of the data.
 *
 * <h2>Recoverability formula</h2>
 * <pre>
 * weightedRate = Σ(failedValue[errorCode] × rate[level(errorCode)]) / totalFailedValue
 * estimatedRecoverableRevenue = revenueAtRisk × weightedRate
 * nonRecoverableRevenue = revenueAtRisk − estimatedRecoverableRevenue
 * </pre>
 * Computing non-recoverable by subtraction (rather than an independent
 * formula) guarantees {@code recoverable + nonRecoverable == revenueAtRisk}
 * exactly, not just approximately.
 */
@Service
@Transactional(readOnly = true)
public class FinancialImpactService {

    private final IncidentRepository incidentRepository;
    private final PaymentRepository paymentRepository;
    private final RecoverabilityProperties recoverabilityProperties;

    public FinancialImpactService(IncidentRepository incidentRepository, PaymentRepository paymentRepository,
                                   RecoverabilityProperties recoverabilityProperties) {
        this.incidentRepository = incidentRepository;
        this.paymentRepository = paymentRepository;
        this.recoverabilityProperties = recoverabilityProperties;
    }

    public FinancialImpactResponse getFinancialImpact(Long incidentId) {
        Incident incident = requireIncident(incidentId);
        List<Payment> batch = resolveAffectedBatch(incident);

        BigDecimal affectedTransactionValue = sumAmount(batch);
        List<Payment> failed = batch.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).toList();
        BigDecimal failedTransactionValue = sumAmount(failed);
        BigDecimal revenueAtRisk = incident.getRevenueAtRisk();

        double weightedRate = weightedRecoverabilityRate(failed, failedTransactionValue);
        BigDecimal estimatedRecoverableRevenue = scale(revenueAtRisk.multiply(BigDecimal.valueOf(weightedRate)));
        BigDecimal nonRecoverableRevenue = scale(revenueAtRisk.subtract(estimatedRecoverableRevenue));

        long affectedCount = batch.size();
        BigDecimal averageTransactionValue = affectedCount == 0
                ? zero()
                : scale(affectedTransactionValue.divide(BigDecimal.valueOf(affectedCount), 10, RoundingMode.HALF_UP));
        BigDecimal largest = batch.stream().map(Payment::getAmount).max(BigDecimal::compareTo).map(this::scale).orElse(zero());
        double recoverablePercentage = revenueAtRisk.compareTo(BigDecimal.ZERO) == 0
                ? 0.0
                : estimatedRecoverableRevenue
                        .divide(revenueAtRisk, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();

        return new FinancialImpactResponse(
                incident.getId(), affectedCount, affectedTransactionValue, failedTransactionValue,
                revenueAtRisk, estimatedRecoverableRevenue, nonRecoverableRevenue,
                averageTransactionValue, largest, recoverablePercentage
        );
    }

    public FinancialBreakdownResponse getFinancialBreakdown(Long incidentId) {
        Incident incident = requireIncident(incidentId);
        List<Payment> batch = resolveAffectedBatch(incident);
        BigDecimal totalFailedValue = sumAmount(batch.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).toList());
        BigDecimal revenueAtRisk = incident.getRevenueAtRisk();

        return new FinancialBreakdownResponse(
                incident.getId(),
                segmentBreakdown(batch, "BANK", Payment::getBank, totalFailedValue, revenueAtRisk),
                segmentBreakdown(batch, "PAYMENT_METHOD", p -> p.getPaymentMethod().name(), totalFailedValue, revenueAtRisk),
                segmentBreakdown(
                        batch.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).toList(),
                        "ERROR_CODE", p -> p.getErrorCode().name(), totalFailedValue, revenueAtRisk),
                segmentBreakdown(batch, "MERCHANT", Payment::getMerchantId, totalFailedValue, revenueAtRisk)
        );
    }

    public IncidentEvidenceDto getEvidence(Long incidentId) {
        Incident incident = requireIncident(incidentId);
        List<Payment> batch = resolveAffectedBatch(incident);
        BigDecimal totalFailedValue = sumAmount(batch.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).toList());
        BigDecimal revenueAtRisk = incident.getRevenueAtRisk();

        List<Payment> failed = batch.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).toList();
        double weightedRate = weightedRecoverabilityRate(failed, totalFailedValue);
        BigDecimal estimatedRecoverableRevenue = scale(revenueAtRisk.multiply(BigDecimal.valueOf(weightedRate)));
        BigDecimal nonRecoverableRevenue = scale(revenueAtRisk.subtract(estimatedRecoverableRevenue));

        return new IncidentEvidenceDto(
                incident.getId(), incident.getType(), incident.getSeverity(),
                incident.getCurrentFailureRate(), incident.getBaselineFailureRate(), incident.getFailureRateIncrease(),
                incident.getAffectedTransactions(), revenueAtRisk, estimatedRecoverableRevenue, nonRecoverableRevenue,
                evidenceMap(batch, "BANK", Payment::getBank, totalFailedValue, revenueAtRisk),
                evidenceMap(batch, "PAYMENT_METHOD", p -> p.getPaymentMethod().name(), totalFailedValue, revenueAtRisk),
                evidenceMap(failed, "ERROR_CODE", p -> p.getErrorCode().name(), totalFailedValue, revenueAtRisk),
                evidenceMap(batch, "MERCHANT", Payment::getMerchantId, totalFailedValue, revenueAtRisk),
                Map.of()
        );
    }

    // ---------------------------------------------------------------
    // Affected batch reconstruction
    // ---------------------------------------------------------------

    /**
     * Reconstructs the incident's affected population from the live
     * {@code payments} table using the incident's own target criteria - no
     * transactions are duplicated or stored separately.
     */
    private List<Payment> resolveAffectedBatch(Incident incident) {
        PaymentMethod method = incident.getTargetPaymentMethod();
        String bank = incident.getTargetBank();
        Specification<Payment> spec = PaymentSpecifications.filter(null, method, bank, null, incident.getStartTime(), null);
        if (incident.getTargetMerchantId() != null) {
            spec = spec.and(PaymentSpecifications.hasMerchantId(incident.getTargetMerchantId()));
        }
        return paymentRepository.findAll(spec);
    }

    private Incident requireIncident(Long id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + id));
    }

    // ---------------------------------------------------------------
    // Recoverability
    // ---------------------------------------------------------------

    /**
     * Value-weighted average recoverability rate across the failed
     * population's actual error-code mix. Returns 0 if there's no failed
     * value to weight against.
     */
    private double weightedRecoverabilityRate(List<Payment> failed, BigDecimal totalFailedValue) {
        if (totalFailedValue.compareTo(BigDecimal.ZERO) == 0) {
            return 0.0;
        }
        Map<ErrorCode, BigDecimal> valueByErrorCode = failed.stream()
                .collect(Collectors.groupingBy(Payment::getErrorCode,
                        Collectors.reducing(BigDecimal.ZERO, Payment::getAmount, BigDecimal::add)));

        BigDecimal weightedSum = BigDecimal.ZERO;
        for (Map.Entry<ErrorCode, BigDecimal> entry : valueByErrorCode.entrySet()) {
            double rate = recoverabilityProperties.rateFor(RecoverabilityClassifier.classify(entry.getKey()));
            weightedSum = weightedSum.add(entry.getValue().multiply(BigDecimal.valueOf(rate)));
        }
        return weightedSum.divide(totalFailedValue, 10, RoundingMode.HALF_UP).doubleValue();
    }

    // ---------------------------------------------------------------
    // Segment breakdowns
    // ---------------------------------------------------------------

    private List<FinancialSegmentDto> segmentBreakdown(List<Payment> population, String dimension,
                                                         Function<Payment, String> keyFn,
                                                         BigDecimal totalFailedValue, BigDecimal revenueAtRisk) {
        Map<String, List<Payment>> grouped = population.stream()
                .collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.toList()));

        List<FinancialSegmentDto> result = new ArrayList<>();
        for (Map.Entry<String, List<Payment>> entry : grouped.entrySet()) {
            List<Payment> segmentPayments = entry.getValue();
            List<Payment> segmentFailed = segmentPayments.stream().filter(p -> p.getStatus() == PaymentStatus.FAILED).toList();
            BigDecimal segmentValue = sumAmount(segmentPayments);
            BigDecimal segmentFailedValue = sumAmount(segmentFailed);

            BigDecimal segmentRevenueAtRisk = apportion(segmentFailedValue, totalFailedValue, revenueAtRisk);
            double segmentRate = weightedRecoverabilityRate(segmentFailed, segmentFailedValue);
            BigDecimal segmentRecoverable = scale(segmentRevenueAtRisk.multiply(BigDecimal.valueOf(segmentRate)));

            result.add(new FinancialSegmentDto(
                    dimension, entry.getKey(), segmentPayments.size(), scale(segmentValue), scale(segmentFailedValue),
                    segmentRevenueAtRisk, segmentRecoverable
            ));
        }

        result.sort((a, b) -> b.revenueAtRisk().compareTo(a.revenueAtRisk()));
        return result;
    }

    private Map<String, SegmentFinancialsDto> evidenceMap(List<Payment> population, String dimensionLabel,
                                                            Function<Payment, String> keyFn,
                                                            BigDecimal totalFailedValue, BigDecimal revenueAtRisk) {
        return segmentBreakdown(population, dimensionLabel, keyFn, totalFailedValue, revenueAtRisk).stream()
                .collect(Collectors.toMap(
                        FinancialSegmentDto::segment,
                        s -> new SegmentFinancialsDto(s.transactionCount(), s.transactionValue(), s.failedValue(),
                                s.revenueAtRisk(), s.estimatedRecoverableRevenue()),
                        (a, b) -> a, LinkedHashMap::new
                ));
    }

    /**
     * Splits {@code total} proportionally by {@code part}'s share of
     * {@code wholeBasis}. Used to apportion an incident's overall
     * revenue-at-risk across segments by each segment's share of the
     * affected population's total failed value, so segment values sum back
     * to the incident total.
     */
    private BigDecimal apportion(BigDecimal part, BigDecimal wholeBasis, BigDecimal total) {
        if (wholeBasis.compareTo(BigDecimal.ZERO) == 0) {
            return zero();
        }
        return scale(part.divide(wholeBasis, 10, RoundingMode.HALF_UP).multiply(total));
    }

    // ---------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------

    private BigDecimal sumAmount(List<Payment> payments) {
        return scale(payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    private BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
}
