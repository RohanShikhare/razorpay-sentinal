package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.AiProvider;
import com.sentinelpulse.ai.AiProviderFactory;
import com.sentinelpulse.ai.InvestigationStatus;
import com.sentinelpulse.ai.MockAiProvider;
import com.sentinelpulse.dto.AiEvidenceDto;
import com.sentinelpulse.dto.IncidentInvestigationResponse;
import com.sentinelpulse.dto.RootCauseHypothesis;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentInvestigation;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.exception.SentinelAiException;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
import com.sentinelpulse.repository.IncidentInvestigationRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IncidentInvestigationServiceTest {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentDiagnosisRepository incidentDiagnosisRepository;

    @Autowired
    private IncidentInvestigationRepository incidentInvestigationRepository;

    @Autowired
    private IncidentEvidenceService incidentEvidenceService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentGeneratorService paymentGeneratorService;

    @Autowired
    private AiProviderFactory aiProviderFactory;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditEventService auditEventService;

    private IncidentInvestigationService investigationService;

    @BeforeEach
    void setUp() {
        incidentInvestigationRepository.deleteAll();
        incidentDiagnosisRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();

        investigationService = new IncidentInvestigationService(
                incidentRepository,
                incidentDiagnosisRepository,
                incidentEvidenceService,
                aiProviderFactory,
                incidentInvestigationRepository,
                objectMapper,
                auditEventService
        );
    }

    private Incident createTestIncident(IncidentType type, String bank, PaymentMethod method, String merchant,
                                        double baseline, double current, double increase) {
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
                baseline,
                current,
                increase,
                bank,
                method,
                merchant,
                60
        );
        return incidentRepository.save(incident);
    }

    @Test
    @DisplayName("Investigate Bank X UPI degradation scenario - ranks Bank X switch degradation as strongest hypothesis")
    void testInvestigateBankXUpiScenario() {
        paymentGeneratorService.generateAndPersist(1_000, 101L);
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null, 4.2, 36.6, 32.4);

        IncidentInvestigationResponse response = investigationService.investigateIncident(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(response.incidentId()).isEqualTo(incident.getId());
        assertThat(response.overallConfidence()).isGreaterThanOrEqualTo(0.90);
        assertThat(response.strongestRootCause()).containsIgnoringCase("Bank X UPI Gateway");
        assertThat(response.affectedSegment()).isEqualTo("Bank X + UPI");
        assertThat(response.hypotheses()).hasSizeGreaterThanOrEqualTo(3);

        RootCauseHypothesis strongest = response.hypotheses().stream()
                .filter(h -> "STRONGEST".equalsIgnoreCase(h.verdict()))
                .findFirst()
                .orElse(null);
        assertThat(strongest).isNotNull();
        assertThat(strongest.hypothesis()).containsIgnoringCase("Bank X UPI");
        assertThat(strongest.supportingEvidence()).isNotEmpty();

        // National switch and merchant misconfig should be refuted
        boolean hasRefuted = response.hypotheses().stream()
                .anyMatch(h -> "REFUTED".equalsIgnoreCase(h.verdict()));
        assertThat(hasRefuted).isTrue();

        // Confirm database persistence
        Optional<IncidentInvestigation> persisted = incidentInvestigationRepository.findFirstByIncidentIdOrderByCreatedAtDesc(incident.getId());
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(InvestigationStatus.COMPLETED);
    }

    @Test
    @DisplayName("Investigate Merchant-Specific degradation scenario - ranks merchant configuration as strongest hypothesis")
    void testInvestigateMerchantScenario() {
        paymentGeneratorService.generateAndPersist(500, 102L);
        Incident incident = createTestIncident(IncidentType.MERCHANT_DEGRADATION, null, null, "merchant_special_99", 3.5, 42.0, 38.5);

        IncidentInvestigationResponse response = investigationService.investigateIncident(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(response.overallConfidence()).isGreaterThanOrEqualTo(0.85);
        assertThat(response.strongestRootCause()).containsIgnoringCase("merchant_special_99");
        assertThat(response.requiresHumanReview()).isTrue();

        RootCauseHypothesis strongest = response.hypotheses().stream()
                .filter(h -> "STRONGEST".equalsIgnoreCase(h.verdict()))
                .findFirst()
                .orElse(null);
        assertThat(strongest).isNotNull();
        assertThat(strongest.hypothesis()).containsIgnoringCase("Merchant");
    }

    @Test
    @DisplayName("Investigate Normal Random Failures scenario - avoids inventing false root cause")
    void testInvestigateNormalNoiseScenario() {
        paymentGeneratorService.generateAndPersist(500, 103L);
        Incident incident = createTestIncident(IncidentType.SYSTEM_DEGRADATION, null, null, null, 3.8, 6.2, 2.4);

        IncidentInvestigationResponse response = investigationService.investigateIncident(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.status()).isEqualTo(InvestigationStatus.COMPLETED);
        assertThat(response.overallConfidence()).isLessThan(0.80);
        assertThat(response.strongestRootCause()).containsIgnoringCase("Diffuse");

        // Low confidence (< 0.70) should have triggered conservative human review flag
        assertThat(response.requiresHumanReview()).isTrue();
    }

    @Test
    @DisplayName("Validation: Rejects malformed JSON and records INVALID_RESPONSE")
    void testValidationMalformedJson() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "HDFC", PaymentMethod.CARD, null, 4.0, 15.0, 11.0);

        AiProvider badProvider = new MockAiProvider(objectMapper) {
            @Override
            public String generateInvestigationJson(AiEvidenceDto evidence, String diagnosisSummary, String systemInstruction) {
                return "MALFORMED_JSON_STRING_NOT_OBJECT";
            }
        };

        AiProviderFactory mockFactory = new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return badProvider;
            }
        };

        IncidentInvestigationService customService = new IncidentInvestigationService(
                incidentRepository,
                incidentDiagnosisRepository,
                incidentEvidenceService,
                mockFactory,
                incidentInvestigationRepository,
                objectMapper,
                auditEventService
        );

        Long id = incident.getId();
        assertThatThrownBy(() -> customService.investigateIncident(id))
                .isInstanceOf(SentinelAiException.class)
                .hasMessageContaining("Malformed JSON");

        Optional<IncidentInvestigation> invalidRecord = incidentInvestigationRepository.findFirstByIncidentIdOrderByCreatedAtDesc(id);
        assertThat(invalidRecord).isPresent();
        assertThat(invalidRecord.get().getStatus()).isEqualTo(InvestigationStatus.INVALID_RESPONSE);
    }

    @Test
    @DisplayName("Validation: Rejects confidence values outside 0.0 - 1.0 range")
    void testValidationOutOfRangeConfidence() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "ICICI", PaymentMethod.UPI, null, 4.0, 20.0, 16.0);

        AiProvider badProvider = new MockAiProvider(objectMapper) {
            @Override
            public String generateInvestigationJson(AiEvidenceDto evidence, String diagnosisSummary, String systemInstruction) {
                return """
                        {
                          "incidentType": "PAYMENT_DEGRADATION",
                          "summary": "Sample summary",
                          "strongestRootCause": "Sample cause",
                          "overallConfidence": 2.5,
                          "affectedSegment": "ICICI + UPI",
                          "hypotheses": [
                            {
                              "hypothesis": "Test Hypothesis",
                              "confidence": 0.8,
                              "supportingEvidence": [],
                              "contradictingEvidence": [],
                              "affectedSegment": "ICICI + UPI",
                              "verdict": "STRONGEST"
                            }
                          ],
                          "supportingEvidence": [],
                          "contradictingEvidence": [],
                          "reasoning": "Sample reasoning",
                          "uncertainty": "Sample uncertainty",
                          "requiresHumanReview": false
                        }
                        """;
            }
        };

        AiProviderFactory mockFactory = new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return badProvider;
            }
        };

        IncidentInvestigationService customService = new IncidentInvestigationService(
                incidentRepository,
                incidentDiagnosisRepository,
                incidentEvidenceService,
                mockFactory,
                incidentInvestigationRepository,
                objectMapper,
                auditEventService
        );

        Long id = incident.getId();
        assertThatThrownBy(() -> customService.investigateIncident(id))
                .isInstanceOf(SentinelAiException.class)
                .hasMessageContaining("Overall confidence score must be between 0.0 and 1.0");
    }

    @Test
    @DisplayName("Validation: Rejects missing required string fields")
    void testValidationMissingRequiredFields() {
        Incident incident = createTestIncident(IncidentType.PAYMENT_DEGRADATION, "SBI", PaymentMethod.NETBANKING, null, 4.0, 20.0, 16.0);

        AiProvider badProvider = new MockAiProvider(objectMapper) {
            @Override
            public String generateInvestigationJson(AiEvidenceDto evidence, String diagnosisSummary, String systemInstruction) {
                return """
                        {
                          "incidentType": "PAYMENT_DEGRADATION",
                          "summary": "",
                          "strongestRootCause": "Sample cause",
                          "overallConfidence": 0.8,
                          "affectedSegment": "SBI + NETBANKING",
                          "hypotheses": [],
                          "supportingEvidence": [],
                          "contradictingEvidence": [],
                          "reasoning": "Sample reasoning",
                          "uncertainty": "Sample uncertainty",
                          "requiresHumanReview": false
                        }
                        """;
            }
        };

        AiProviderFactory mockFactory = new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return badProvider;
            }
        };

        IncidentInvestigationService customService = new IncidentInvestigationService(
                incidentRepository,
                incidentDiagnosisRepository,
                incidentEvidenceService,
                mockFactory,
                incidentInvestigationRepository,
                objectMapper,
                auditEventService
        );

        Long id = incident.getId();
        assertThatThrownBy(() -> customService.investigateIncident(id))
                .isInstanceOf(SentinelAiException.class)
                .hasMessageContaining("Missing or blank required field: 'summary'");
    }
}
