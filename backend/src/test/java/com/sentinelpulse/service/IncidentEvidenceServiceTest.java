package com.sentinelpulse.service;

import com.sentinelpulse.dto.AiEvidenceDto;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class IncidentEvidenceServiceTest {

    @Autowired
    private IncidentEvidenceService incidentEvidenceService;

    @Autowired
    private PaymentGeneratorService paymentGeneratorService;

    @Autowired
    private IncidentSimulationService simulationService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FinancialImpactService financialImpactService;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    void generatesDeterministicEvidenceFromDeterministicIncidentAndFinancialData() {
        paymentGeneratorService.generateAndPersist(1_000, 42L);

        Instant now = Instant.now();
        Incident incident = new Incident(
                IncidentType.PAYMENT_DEGRADATION,
                IncidentSeverity.HIGH,
                now,
                now.minusSeconds(3600),
                50,
                BigDecimal.valueOf(100_000),
                BigDecimal.valueOf(30_000),
                BigDecimal.valueOf(25_000),
                85.0,
                IncidentStatus.DETECTED,
                5.0,
                32.0,
                27.0,
                "Bank X",
                PaymentMethod.UPI,
                null,
                60
        );
        incident = incidentRepository.save(incident);

        AiEvidenceDto evidence1 = incidentEvidenceService.buildEvidence(incident.getId());
        AiEvidenceDto evidence2 = incidentEvidenceService.buildEvidence(incident.getId());

        assertNotNull(evidence1);
        assertEquals(incident.getId(), evidence1.incidentId());
        assertEquals(IncidentType.PAYMENT_DEGRADATION, evidence1.incidentType());
        assertEquals("Bank X", evidence1.targetBank());
        assertEquals(PaymentMethod.UPI, evidence1.targetPaymentMethod());
        assertEquals(0, BigDecimal.valueOf(25_000).compareTo(evidence1.revenueAtRisk()));

        // Deterministic repeat check
        assertEquals(evidence1.affectedTransactionCount(), evidence2.affectedTransactionCount());
        assertEquals(evidence1.revenueAtRisk(), evidence2.revenueAtRisk());
        assertEquals(evidence1.estimatedRecoverableRevenue(), evidence2.estimatedRecoverableRevenue());
        assertEquals(evidence1.nonRecoverableRevenue(), evidence2.nonRecoverableRevenue());

        // Financial integrity: nonRecoverable + recoverable = revenueAtRisk
        BigDecimal sum = evidence1.estimatedRecoverableRevenue().add(evidence1.nonRecoverableRevenue());
        assertEquals(evidence1.revenueAtRisk(), sum);

        assertNotNull(evidence1.bankBreakdown());
        assertNotNull(evidence1.paymentMethodBreakdown());
        assertNotNull(evidence1.errorCodeBreakdown());
        assertNotNull(evidence1.concentrationSummary());
    }
}
