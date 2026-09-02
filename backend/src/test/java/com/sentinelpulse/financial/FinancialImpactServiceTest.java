package com.sentinelpulse.financial;

import com.sentinelpulse.dto.FinancialBreakdownResponse;
import com.sentinelpulse.dto.FinancialImpactResponse;
import com.sentinelpulse.entity.Banks;
import com.sentinelpulse.entity.ErrorCode;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.Payment;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.PaymentStatus;
import com.sentinelpulse.exception.ResourceNotFoundException;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Uses hand-built payments with known amounts (not the random generator) so
 * the financial formulas can be checked against exact expected values.
 */
@SpringBootTest
class FinancialImpactServiceTest {

    @Autowired
    private FinancialImpactService financialImpactService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    private Instant incidentStart;

    @BeforeEach
    void cleanDatabase() {
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
        incidentStart = Instant.now().minus(1, ChronoUnit.HOURS);
    }

    private Payment payment(BigDecimal amount, PaymentStatus status, ErrorCode errorCode) {
        return new Payment("merchant_001", "customer_00001", amount, "INR",
                Instant.now(), PaymentMethod.UPI, Banks.BANK_X, status, errorCode);
    }

    private Incident incident(BigDecimal revenueAtRisk) {
        return incidentRepository.save(new Incident(
                IncidentType.PAYMENT_DEGRADATION, IncidentSeverity.CRITICAL, Instant.now(), incidentStart,
                0, BigDecimal.ZERO, BigDecimal.ZERO, revenueAtRisk, 80.0, IncidentStatus.DETECTED,
                5.0, 32.0, 27.0, Banks.BANK_X, PaymentMethod.UPI, null, 60
        ));
    }

    @Test
    void affectedTransactionValueSumsTheKnownAmounts() {
        paymentRepository.save(payment(new BigDecimal("100.00"), PaymentStatus.SUCCESS, ErrorCode.NONE));
        paymentRepository.save(payment(new BigDecimal("200.00"), PaymentStatus.SUCCESS, ErrorCode.NONE));
        paymentRepository.save(payment(new BigDecimal("300.00"), PaymentStatus.FAILED, ErrorCode.BANK_TIMEOUT));

        Incident inc = incident(new BigDecimal("300.00"));
        FinancialImpactResponse result = financialImpactService.getFinancialImpact(inc.getId());

        assertEquals(3, result.affectedTransactions());
        assertEquals(0, new BigDecimal("600.00").compareTo(result.affectedTransactionValue()));
        assertEquals(0, new BigDecimal("300.00").compareTo(result.failedTransactionValue()));
    }

    @Test
    void recoverableAndNonRecoverableAlwaysSumExactlyToRevenueAtRisk() {
        paymentRepository.save(payment(new BigDecimal("1000.00"), PaymentStatus.FAILED, ErrorCode.BANK_TIMEOUT));
        paymentRepository.save(payment(new BigDecimal("1000.00"), PaymentStatus.FAILED, ErrorCode.INSUFFICIENT_FUNDS));

        Incident inc = incident(new BigDecimal("777.77"));
        FinancialImpactResponse result = financialImpactService.getFinancialImpact(inc.getId());

        BigDecimal sum = result.estimatedRecoverableRevenue().add(result.nonRecoverableRevenue());
        assertEquals(0, result.revenueAtRisk().compareTo(sum),
                "recoverable + nonRecoverable must equal revenueAtRisk exactly");
        assertTrue(result.estimatedRecoverableRevenue().compareTo(result.revenueAtRisk()) <= 0,
                "recoverable must never exceed revenueAtRisk");
    }

    @Test
    void recoverableRevenueReflectsTheWeightedErrorCodeMix() {
        // BANK_TIMEOUT -> HIGH_RECOVERABILITY (default rate 0.70)
        // INSUFFICIENT_FUNDS -> LOW_RECOVERABILITY (default rate 0.10)
        // Equal value split -> weighted rate = (0.70 + 0.10) / 2 = 0.40
        paymentRepository.save(payment(new BigDecimal("1000.00"), PaymentStatus.FAILED, ErrorCode.BANK_TIMEOUT));
        paymentRepository.save(payment(new BigDecimal("1000.00"), PaymentStatus.FAILED, ErrorCode.INSUFFICIENT_FUNDS));

        Incident inc = incident(new BigDecimal("2000.00"));
        FinancialImpactResponse result = financialImpactService.getFinancialImpact(inc.getId());

        assertEquals(0, new BigDecimal("800.00").compareTo(result.estimatedRecoverableRevenue()),
                "expected 2000 * 0.40 = 800, got " + result.estimatedRecoverableRevenue());
        assertEquals(0, new BigDecimal("1200.00").compareTo(result.nonRecoverableRevenue()));
    }

    @Test
    void zeroAffectedTransactionsProducesZeroWithoutDivisionByZero() {
        // No payments saved at all - the incident's target segment has no data.
        Incident inc = incident(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        FinancialImpactResponse result = financialImpactService.getFinancialImpact(inc.getId());

        assertEquals(0, result.affectedTransactions());
        assertEquals(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).compareTo(result.averageTransactionValue()));
        assertEquals(0, BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP).compareTo(result.estimatedRecoverableRevenue()));
        assertEquals(0.0, result.recoverablePercentage());
    }

    @Test
    void largestAffectedTransactionIsCorrectlyIdentified() {
        paymentRepository.save(payment(new BigDecimal("100.00"), PaymentStatus.SUCCESS, ErrorCode.NONE));
        paymentRepository.save(payment(new BigDecimal("450.50"), PaymentStatus.FAILED, ErrorCode.BANK_TIMEOUT));
        paymentRepository.save(payment(new BigDecimal("300.00"), PaymentStatus.SUCCESS, ErrorCode.NONE));

        Incident inc = incident(new BigDecimal("450.50"));
        FinancialImpactResponse result = financialImpactService.getFinancialImpact(inc.getId());

        assertEquals(0, new BigDecimal("450.50").compareTo(result.largestAffectedTransaction()));
    }

    @Test
    void recoverablePercentageMatchesFormula() {
        paymentRepository.save(payment(new BigDecimal("1000.00"), PaymentStatus.FAILED, ErrorCode.BANK_TIMEOUT));

        Incident inc = incident(new BigDecimal("1000.00"));
        FinancialImpactResponse result = financialImpactService.getFinancialImpact(inc.getId());

        double expectedPercentage = result.estimatedRecoverableRevenue()
                .divide(result.revenueAtRisk(), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
        assertEquals(expectedPercentage, result.recoverablePercentage(), 0.01);
    }

    @Test
    void breakdownByErrorCodeSegmentsSumBackToRevenueAtRisk() {
        paymentRepository.save(payment(new BigDecimal("600.00"), PaymentStatus.FAILED, ErrorCode.BANK_TIMEOUT));
        paymentRepository.save(payment(new BigDecimal("400.00"), PaymentStatus.FAILED, ErrorCode.ISSUER_DECLINED));

        Incident inc = incident(new BigDecimal("1000.00"));
        FinancialBreakdownResponse breakdown = financialImpactService.getFinancialBreakdown(inc.getId());

        BigDecimal sum = breakdown.byErrorCode().stream()
                .map(FinancialImpactServiceTest::revenueAtRisk)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("1000.00").compareTo(sum),
                "segment revenueAtRisk values should sum back to the incident total, got " + sum);
    }

    private static BigDecimal revenueAtRisk(com.sentinelpulse.dto.FinancialSegmentDto segment) {
        return segment.revenueAtRisk();
    }

    @Test
    void unknownIncidentThrowsResourceNotFound() {
        assertThrows(ResourceNotFoundException.class, () -> financialImpactService.getFinancialImpact(999_999L));
    }
}
