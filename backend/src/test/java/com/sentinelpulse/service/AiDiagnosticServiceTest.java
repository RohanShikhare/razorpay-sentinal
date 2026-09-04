package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.AiProvider;
import com.sentinelpulse.ai.AiProviderFactory;
import com.sentinelpulse.ai.ControlledAction;
import com.sentinelpulse.ai.DiagnosisStatus;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.dto.AiDiagnosisResponse;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentDiagnosis;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.exception.SentinelAiException;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AiDiagnosticServiceTest {

    @Autowired
    private AiDiagnosticService aiDiagnosticService;

    @Autowired
    private PaymentGeneratorService paymentGeneratorService;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentDiagnosisRepository incidentDiagnosisRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IncidentEvidenceService incidentEvidenceService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        incidentDiagnosisRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    private Incident createTestIncident(IncidentType type, String bank, PaymentMethod method, String merchant) {
        Instant now = Instant.now();
        Incident incident = new Incident(
                type,
                IncidentSeverity.HIGH,
                now,
                now.minusSeconds(3600),
                100,
                BigDecimal.valueOf(200_000),
                BigDecimal.valueOf(60_000),
                BigDecimal.valueOf(50_000),
                88.0,
                IncidentStatus.DETECTED,
                5.0,
                32.0,
                27.0,
                bank,
                method,
                merchant,
                60
        );
        return incidentRepository.save(incident);
    }

    @Test
    void diagnosesBankXUpiIncidentSuccessfully() {
        paymentGeneratorService.generateAndPersist(1_000, 101L);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        AiDiagnosisResponse response = aiDiagnosticService.diagnoseIncident(incident.getId());

        assertNotNull(response);
        assertEquals(incident.getId(), response.incidentId());
        assertEquals(DiagnosisStatus.COMPLETED, response.status());
        assertEquals("MOCK", response.provider());
        assertEquals(IncidentType.PAYMENT_DEGRADATION, response.incidentType());
        assertTrue(response.rootCause().contains("Bank X UPI"));
        assertTrue(response.confidence() >= 0.0 && response.confidence() <= 1.0);
        assertTrue(response.estimatedRecoveryPercentage() >= 0.0 && response.estimatedRecoveryPercentage() <= 100.0);
        assertNotNull(response.recommendedAction());
        assertNotNull(response.riskLevel());

        // Incident rootCause updated
        Incident updatedIncident = incidentRepository.findById(incident.getId()).orElseThrow();
        assertEquals(response.rootCause(), updatedIncident.getRootCause());
        assertEquals(IncidentStatus.INVESTIGATING, updatedIncident.getStatus());

        // Persisted diagnosis
        Optional<AiDiagnosisResponse> latest = aiDiagnosticService.getLatestDiagnosis(incident.getId());
        assertTrue(latest.isPresent());
        assertEquals(response.diagnosisId(), latest.get().diagnosisId());
    }

    @Test
    void diagnosesMerchantDegradationIncidentSuccessfully() {
        paymentGeneratorService.generateAndPersist(1_000, 102L);
        Incident incident = createTestIncident(IncidentType.MERCHANT_DEGRADATION, null, null, "merchant_007");

        AiDiagnosisResponse response = aiDiagnosticService.diagnoseIncident(incident.getId());

        assertNotNull(response);
        assertEquals(IncidentType.MERCHANT_DEGRADATION, response.incidentType());
        assertTrue(response.rootCause().contains("merchant_007"));
        assertEquals(ControlledAction.HUMAN_REVIEW, response.recommendedAction());
        assertTrue(response.requiresHumanApproval());
    }

    @Test
    void rejectsMalformedJsonAndRecordsInvalidResponse() {
        paymentGeneratorService.generateAndPersist(500, 103L);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        AiProvider brokenProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "TEST_BROKEN";
            }

            @Override
            public String getModelName() {
                return "broken-model";
            }

            @Override
            public String generateDiagnosisJson(com.sentinelpulse.dto.AiEvidenceDto evidence, String instruction) {
                return "THIS IS NOT JSON";
            }
        };

        AiProviderFactory mockFactory = new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return brokenProvider;
            }
        };

        AiDiagnosticService customService = new AiDiagnosticService(
                incidentRepository, incidentEvidenceService, mockFactory, incidentDiagnosisRepository, objectMapper
        );

        assertThrows(SentinelAiException.class, () -> customService.diagnoseIncident(incident.getId()));

        IncidentDiagnosis diagnosis = incidentDiagnosisRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incident.getId()).orElseThrow();
        assertEquals(DiagnosisStatus.INVALID_RESPONSE, diagnosis.getStatus());
        assertEquals("TEST_BROKEN", diagnosis.getProvider());
    }

    @Test
    void rejectsMissingRequiredFields() {
        paymentGeneratorService.generateAndPersist(500, 104L);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        AiProvider missingFieldProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "TEST_MISSING";
            }

            @Override
            public String getModelName() {
                return "missing-field-model";
            }

            @Override
            public String generateDiagnosisJson(com.sentinelpulse.dto.AiEvidenceDto evidence, String instruction) {
                // Missing confidence and recommendedAction
                return """
                        {
                          "incidentType": "PAYMENT_DEGRADATION",
                          "rootCause": "Timeout issue",
                          "reasoning": "High timeouts"
                        }
                        """;
            }
        };

        AiProviderFactory mockFactory = new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return missingFieldProvider;
            }
        };

        AiDiagnosticService customService = new AiDiagnosticService(
                incidentRepository, incidentEvidenceService, mockFactory, incidentDiagnosisRepository, objectMapper
        );

        assertThrows(SentinelAiException.class, () -> customService.diagnoseIncident(incident.getId()));
    }

    @Test
    void rejectsInvalidConfidenceScoreOutOfRange() {
        paymentGeneratorService.generateAndPersist(500, 105L);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        AiProvider invalidConfidenceProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "TEST_INVALID";
            }

            @Override
            public String getModelName() {
                return "invalid-model";
            }

            @Override
            public String generateDiagnosisJson(com.sentinelpulse.dto.AiEvidenceDto evidence, String instruction) {
                return """
                        {
                          "incidentType": "PAYMENT_DEGRADATION",
                          "rootCause": "Timeout issue",
                          "reasoning": "High timeouts",
                          "confidence": 1.5,
                          "evidence": ["e1"],
                          "affectedSegment": "Bank X",
                          "recommendedAction": "DELAYED_RETRY",
                          "estimatedRecoveryPercentage": 50.0,
                          "riskLevel": "HIGH",
                          "requiresHumanApproval": false
                        }
                        """;
            }
        };

        AiProviderFactory mockFactory = new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return invalidConfidenceProvider;
            }
        };

        AiDiagnosticService customService = new AiDiagnosticService(
                incidentRepository, incidentEvidenceService, mockFactory, incidentDiagnosisRepository, objectMapper
        );

        assertThrows(SentinelAiException.class, () -> customService.diagnoseIncident(incident.getId()));
    }

    @Test
    void rejectsUnsupportedActionEnum() {
        paymentGeneratorService.generateAndPersist(500, 106L);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null);

        AiProvider invalidActionProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "TEST_INVALID_ACTION";
            }

            @Override
            public String getModelName() {
                return "invalid-action-model";
            }

            @Override
            public String generateDiagnosisJson(com.sentinelpulse.dto.AiEvidenceDto evidence, String instruction) {
                return """
                        {
                          "incidentType": "PAYMENT_DEGRADATION",
                          "rootCause": "Timeout issue",
                          "reasoning": "High timeouts",
                          "confidence": 0.85,
                          "evidence": ["e1"],
                          "affectedSegment": "Bank X",
                          "recommendedAction": "SHUT_DOWN_SERVER_UNCONTROLLED",
                          "estimatedRecoveryPercentage": 50.0,
                          "riskLevel": "HIGH",
                          "requiresHumanApproval": false
                        }
                        """;
            }
        };

        AiProviderFactory mockFactory = new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return invalidActionProvider;
            }
        };

        AiDiagnosticService customService = new AiDiagnosticService(
                incidentRepository, incidentEvidenceService, mockFactory, incidentDiagnosisRepository, objectMapper
        );

        assertThrows(SentinelAiException.class, () -> customService.diagnoseIncident(incident.getId()));
    }
}
