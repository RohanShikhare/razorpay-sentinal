package com.sentinelpulse.service;

import com.sentinelpulse.config.MonitoringProperties;
import com.sentinelpulse.config.RecoverabilityProperties;
import com.sentinelpulse.dto.AiEvidenceDto;
import com.sentinelpulse.dto.AiEvidenceDto.ConcentrationSummary;
import com.sentinelpulse.dto.AiEvidenceDto.ErrorCodeDetail;
import com.sentinelpulse.dto.AiEvidenceDto.SegmentDetail;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.dto.SegmentBreakdownDto;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.financial.RecoverabilityClassifier;
import com.sentinelpulse.monitoring.SegmentAnalyzer;
import com.sentinelpulse.monitoring.SegmentAnalyzer.SegmentSignal;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.PaymentSpecifications;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Constructs a compact, structured evidence object from the deterministic monitoring
 * and financial systems for AI consumption.
 */
@Service
@Transactional(readOnly = true)
public class IncidentEvidenceService {

    private final IncidentRepository incidentRepository;
    private final PaymentRepository paymentRepository;
    private final FinancialImpactService financialImpactService;
    private final RecoverabilityProperties recoverabilityProperties;
    private final MonitoringProperties monitoringProperties;

    public IncidentEvidenceService(IncidentRepository incidentRepository,
                                   PaymentRepository paymentRepository,
                                   FinancialImpactService financialImpactService,
                                   RecoverabilityProperties recoverabilityProperties,
                                   MonitoringProperties monitoringProperties) {
        this.incidentRepository = incidentRepository;
        this.paymentRepository = paymentRepository;
        this.financialImpactService = financialImpactService;
        this.recoverabilityProperties = recoverabilityProperties;
        this.monitoringProperties = monitoringProperties;
    }

    public AiEvidenceDto buildEvidence(Long incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new ResourceNotFoundException("Incident not found: " + incidentId));

        FinancialImpactResponse financial = financialImpactService.getFinancialImpact(incidentId);
        List<Payment> affectedBatch = resolveAffectedBatch(incident);
        List<Payment> failedBatch = affectedBatch.stream()
                .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                .toList();

        long totalCount = affectedBatch.size();
        long failedCount = failedBatch.size();
        BigDecimal totalFailedValue = financial.failedTransactionValue();

        // 1. Segment breakdowns
        Map<String, SegmentDetail> bankBreakdown = computeSegmentDetails(affectedBatch, Payment::getBank, failedCount);
        Map<String, SegmentDetail> paymentMethodBreakdown = computeSegmentDetails(affectedBatch, p -> p.getPaymentMethod().name(), failedCount);
        Map<String, SegmentDetail> merchantBreakdown = computeSegmentDetails(affectedBatch, Payment::getMerchantId, failedCount);
        Map<String, ErrorCodeDetail> errorCodeBreakdown = computeErrorCodeDetails(failedBatch, failedCount);

        // 2. Concentration summary
        ConcentrationSummary concentrationSummary = computeConcentration(
                bankBreakdown, paymentMethodBreakdown, errorCodeBreakdown, merchantBreakdown
        );

        // 3. Comparative baseline vs current signals
        List<Payment> baseline = paymentRepository.findAll(PaymentSpecifications.timestampTo(incident.getStartTime()));
        List<Payment> current = paymentRepository.findAll(PaymentSpecifications.timestampFrom(incident.getStartTime()));
        List<SegmentSignal> signals = SegmentAnalyzer.evaluate(
                baseline, current, monitoringProperties.getMinimumSegmentSampleSize()
        );

        List<SegmentBreakdownDto> topSegments = signals.stream()
                .limit(8)
                .map(s -> new SegmentBreakdownDto(
                        s.dimension().name(), s.label(),
                        s.baseline().total(), s.current().total(),
                        s.baseline().failureRate(), s.current().failureRate(), s.failureRateIncrease()
                ))
                .toList();

        Double relativeIncreasePercent = incident.getBaselineFailureRate() > 0
                ? round2((incident.getCurrentFailureRate() - incident.getBaselineFailureRate()) / incident.getBaselineFailureRate() * 100.0)
                : null;

        return new AiEvidenceDto(
                incident.getId(),
                incident.getType(),
                incident.getSeverity(),
                incident.getDetectedAt(),
                incident.getAffectedTransactions(),
                incident.getCurrentFailureRate(),
                incident.getBaselineFailureRate(),
                incident.getFailureRateIncrease(),
                relativeIncreasePercent,
                incident.getStatus(),
                incident.getTargetBank(),
                incident.getTargetPaymentMethod(),
                incident.getTargetMerchantId(),
                financial.affectedTransactionValue(),
                financial.failedTransactionValue(),
                financial.revenueAtRisk(),
                financial.estimatedRecoverableRevenue(),
                financial.nonRecoverableRevenue(),
                financial.averageTransactionValue(),
                bankBreakdown,
                paymentMethodBreakdown,
                errorCodeBreakdown,
                merchantBreakdown,
                concentrationSummary,
                topSegments
        );
    }

    private List<Payment> resolveAffectedBatch(Incident incident) {
        PaymentMethod method = incident.getTargetPaymentMethod();
        String bank = incident.getTargetBank();
        Specification<Payment> spec = PaymentSpecifications.filter(null, method, bank, null, incident.getStartTime(), null);
        if (incident.getTargetMerchantId() != null) {
            spec = spec.and(PaymentSpecifications.hasMerchantId(incident.getTargetMerchantId()));
        }
        return paymentRepository.findAll(spec);
    }

    private Map<String, SegmentDetail> computeSegmentDetails(List<Payment> batch,
                                                             Function<Payment, String> keyFn,
                                                             long totalFailuresInBatch) {
        Map<String, List<Payment>> grouped = batch.stream()
                .collect(Collectors.groupingBy(keyFn, LinkedHashMap::new, Collectors.toList()));

        Map<String, SegmentDetail> result = new LinkedHashMap<>();
        for (Map.Entry<String, List<Payment>> entry : grouped.entrySet()) {
            List<Payment> segmentPayments = entry.getValue();
            long count = segmentPayments.size();
            BigDecimal value = sumAmount(segmentPayments);

            List<Payment> failed = segmentPayments.stream()
                    .filter(p -> p.getStatus() == PaymentStatus.FAILED)
                    .toList();
            long failedCount = failed.size();
            BigDecimal failedValue = sumAmount(failed);

            double failureRate = count == 0 ? 0.0 : round2((double) failedCount / count * 100.0);
            double failureShare = totalFailuresInBatch == 0 ? 0.0 : round2((double) failedCount / totalFailuresInBatch * 100.0);

            result.put(entry.getKey(), new SegmentDetail(count, value, failedCount, failedValue, failureRate, failureShare));
        }
        return result;
    }

    private Map<String, ErrorCodeDetail> computeErrorCodeDetails(List<Payment> failedBatch, long totalFailures) {
        Map<ErrorCode, List<Payment>> grouped = failedBatch.stream()
                .collect(Collectors.groupingBy(Payment::getErrorCode, LinkedHashMap::new, Collectors.toList()));

        Map<String, ErrorCodeDetail> result = new LinkedHashMap<>();
        for (Map.Entry<ErrorCode, List<Payment>> entry : grouped.entrySet()) {
            ErrorCode code = entry.getKey();
            List<Payment> payments = entry.getValue();
            long count = payments.size();
            BigDecimal failedValue = sumAmount(payments);
            String level = RecoverabilityClassifier.classify(code).name();
            double rate = recoverabilityProperties.rateFor(RecoverabilityClassifier.classify(code));
            double share = totalFailures == 0 ? 0.0 : round2((double) count / totalFailures * 100.0);

            result.put(code.name(), new ErrorCodeDetail(code.name(), count, failedValue, level, rate, share));
        }
        return result;
    }

    private ConcentrationSummary computeConcentration(
            Map<String, SegmentDetail> bankBreakdown,
            Map<String, SegmentDetail> methodBreakdown,
            Map<String, ErrorCodeDetail> errorBreakdown,
            Map<String, SegmentDetail> merchantBreakdown
    ) {
        var domBank = bankBreakdown.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().failurePercentageShare()))
                .orElse(null);

        var domMethod = methodBreakdown.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().failurePercentageShare()))
                .orElse(null);

        var domError = errorBreakdown.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().shareOfFailures()))
                .orElse(null);

        var domMerchant = merchantBreakdown.entrySet().stream()
                .max(Comparator.comparingDouble(e -> e.getValue().failurePercentageShare()))
                .orElse(null);

        return new ConcentrationSummary(
                domBank != null ? domBank.getKey() : null,
                domBank != null ? domBank.getValue().failurePercentageShare() : null,
                domMethod != null ? domMethod.getKey() : null,
                domMethod != null ? domMethod.getValue().failurePercentageShare() : null,
                domError != null ? domError.getKey() : null,
                domError != null ? domError.getValue().shareOfFailures() : null,
                domMerchant != null ? domMerchant.getKey() : null,
                domMerchant != null ? domMerchant.getValue().failurePercentageShare() : null
        );
    }

    private BigDecimal sumAmount(List<Payment> payments) {
        return payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static double round2(double val) {
        return BigDecimal.valueOf(val).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
