package com.sentinelpulse.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelpulse.ai.AiProvider;
import com.sentinelpulse.ai.AiProviderFactory;
import com.sentinelpulse.ai.MockAiProvider;
import com.sentinelpulse.ai.RecoveryStrategyType;
import com.sentinelpulse.ai.RiskLevel;
import com.sentinelpulse.ai.StrategySource;
import com.sentinelpulse.ai.StrategyStatus;
import com.sentinelpulse.dto.AiEvidenceDto;
import com.sentinelpulse.dto.RecoveryStrategyResponse;
import com.sentinelpulse.entity.Incident;
import com.sentinelpulse.entity.IncidentSeverity;
import com.sentinelpulse.entity.IncidentStatus;
import com.sentinelpulse.entity.IncidentType;
import com.sentinelpulse.entity.PaymentMethod;
import com.sentinelpulse.entity.RecoveryStrategy;
import com.sentinelpulse.financial.FinancialImpactService;
import com.sentinelpulse.repository.IncidentDiagnosisRepository;
import com.sentinelpulse.repository.IncidentInvestigationRepository;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import com.sentinelpulse.repository.RecoveryStrategyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RecoveryStrategyServiceTest {

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private IncidentDiagnosisRepository incidentDiagnosisRepository;

    @Autowired
    private IncidentInvestigationRepository incidentInvestigationRepository;

    @Autowired
    private RecoveryStrategyRepository recoveryStrategyRepository;

    @Autowired
    private FinancialImpactService financialImpactService;

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

    private RecoveryStrategyService recoveryStrategyService;

    @BeforeEach
    void setUp() {
        recoveryStrategyRepository.deleteAll();
        incidentInvestigationRepository.deleteAll();
        incidentDiagnosisRepository.deleteAll();
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();

        recoveryStrategyService = new RecoveryStrategyService(
                incidentRepository,
                financialImpactService,
                incidentEvidenceService,
                incidentInvestigationRepository,
                incidentDiagnosisRepository,
                recoveryStrategyRepository,
                aiProviderFactory,
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
    @DisplayName("Test 1: Bank X + UPI degradation produces bounded DELAYED_RETRY strategy with alternatives")
    void testBankXUpiDegradation_generatesDelayedRetryWithBoundedStoppingConditions() {
        Incident incident = createTestIncident(
                IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null,
                3.5, 78.5, 75.0
        );

        RecoveryStrategyResponse response = recoveryStrategyService.generateStrategy(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.incidentId()).isEqualTo(incident.getId());
        assertThat(response.selectedStrategy()).isEqualTo(RecoveryStrategyType.DELAYED_RETRY);
        assertThat(response.confidence()).isGreaterThanOrEqualTo(0.80);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.MEDIUM);
        assertThat(response.affectedSegment()).contains("Bank X + UPI");
        assertThat(response.expectedRecoveryPercentage()).isEqualTo(70.0);
        assertThat(response.requiresHumanApproval()).isFalse();

        // Stopping conditions
        assertThat(response.stoppingConditions()).isNotNull();
        assertThat(response.stoppingConditions().maxRetryAttempts()).isEqualTo(2);
        assertThat(response.stoppingConditions().retryWindowMinutes()).isEqualTo(30);
        assertThat(response.stoppingConditions().backoffBaseSeconds()).isEqualTo(30);
        assertThat(response.stoppingConditions().stopOnSuccess()).isTrue();
        assertThat(response.stoppingConditions().stopOnIncidentResolved()).isTrue();
        assertThat(response.stoppingConditions().conditions()).isNotEmpty();

        // Alternatives
        assertThat(response.alternatives()).isNotEmpty();
        assertThat(response.alternatives()).anyMatch(a -> a.strategy() == RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD);
        assertThat(response.alternatives()).anyMatch(a -> a.strategy() == RecoveryStrategyType.MONITOR_ONLY);

        // Financial validation
        assertThat(response.authoritativeRevenueAtRisk()).isEqualByComparingTo(incident.getRevenueAtRisk());
        assertThat(response.estimatedRecoveryAmount()).isNotNull();
        assertThat(response.estimatedRecoveryAmount()).isLessThanOrEqualTo(response.authoritativeRecoverableAmount());
    }

    @Test
    @DisplayName("Test 2: Merchant-specific degradation produces HUMAN_REVIEW with no aggressive retries")
    void testMerchantDegradation_generatesHumanReviewWithNoAggressiveRetry() {
        Incident incident = createTestIncident(
                IncidentType.MERCHANT_DEGRADATION, null, null, "merchant_hot",
                2.0, 65.0, 63.0
        );

        RecoveryStrategyResponse response = recoveryStrategyService.generateStrategy(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.selectedStrategy()).isEqualTo(RecoveryStrategyType.HUMAN_REVIEW);
        assertThat(response.requiresHumanApproval()).isTrue();
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.affectedSegment()).contains("merchant_hot");
        assertThat(response.rationale()).contains("merchant");

        // Stopping conditions: locked retry attempts
        assertThat(response.stoppingConditions().maxRetryAttempts()).isEqualTo(0);
        assertThat(response.stoppingConditions().conditions()).anyMatch(c -> c.toLowerCase().contains("human"));
    }

    @Test
    @DisplayName("Test 3: Normal / random noise produces MONITOR_ONLY with LOW risk")
    void testNormalRandomNoise_generatesMonitorOnlyWithLowRisk() {
        Incident incident = createTestIncident(
                IncidentType.SYSTEM_DEGRADATION, null, null, null,
                3.0, 8.5, 5.5
        );

        RecoveryStrategyResponse response = recoveryStrategyService.generateStrategy(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.selectedStrategy()).isEqualTo(RecoveryStrategyType.MONITOR_ONLY);
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.stoppingConditions().maxRetryAttempts()).isEqualTo(0);
    }

    private AiProviderFactory createCustomFactory(AiProvider provider) {
        return new AiProviderFactory(null, null, null) {
            @Override
            public AiProvider getProvider() {
                return provider;
            }
        };
    }

    @Test
    @DisplayName("Test 4: Low confidence (< 0.70) forces human approval requirement")
    void testLowConfidenceInvestigation_forcesHumanApproval() {
        Incident incident = createTestIncident(
                IncidentType.SYSTEM_DEGRADATION, null, null, null,
                3.0, 8.5, 5.5
        );

        // Custom provider with low confidence but false for requiresHumanApproval
        AiProvider lowConfProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "LOW_CONF_MOCK";
            }

            @Override
            public String getModelName() {
                return "low-conf-v1";
            }

            @Override
            public String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction) {
                return "{}";
            }

            @Override
            public String generateRecoveryStrategyJson(AiEvidenceDto evidence, String diagnosisSummary, String investigationSummary, String systemInstruction) {
                return """
                        {
                          "selectedStrategy": "IMMEDIATE_RETRY",
                          "rank": 1,
                          "rationale": "Speculative retry despite low confidence",
                          "confidence": 0.50,
                          "riskLevel": "LOW",
                          "affectedSegment": "Global",
                          "expectedRecoveryPercentage": 40.0,
                          "requiresHumanApproval": false,
                          "stoppingConditions": {
                            "maxRetryAttempts": 1,
                            "retryWindowMinutes": 10,
                            "backoffBaseSeconds": 0,
                            "stopOnSuccess": true,
                            "stopOnIncidentResolved": true,
                            "failureRateThreshold": 50.0,
                            "escalateOnFailureLimit": true,
                            "conditions": ["1 retry attempt"]
                          },
                          "alternatives": []
                        }
                        """;
            }
        };

        RecoveryStrategyService customService = new RecoveryStrategyService(
                incidentRepository, financialImpactService, incidentEvidenceService,
                incidentInvestigationRepository, incidentDiagnosisRepository,
                recoveryStrategyRepository, createCustomFactory(lowConfProvider), objectMapper, auditEventService
        );

        RecoveryStrategyResponse response = customService.generateStrategy(incident.getId());

        assertThat(response.confidence()).isEqualTo(0.50);
        // Safety guardrail must force human approval when confidence < 0.70
        assertThat(response.requiresHumanApproval()).isTrue();
    }

    @Test
    @DisplayName("Test 5: HIGH / CRITICAL risk level forces human approval requirement")
    void testHighRiskIncident_forcesHumanApproval() {
        Incident incident = createTestIncident(
                IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null,
                3.5, 78.5, 75.0
        );

        AiProvider highRiskProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "HIGH_RISK_MOCK";
            }

            @Override
            public String getModelName() {
                return "high-risk-v1";
            }

            @Override
            public String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction) {
                return "{}";
            }

            @Override
            public String generateRecoveryStrategyJson(AiEvidenceDto evidence, String diagnosisSummary, String investigationSummary, String systemInstruction) {
                return """
                        {
                          "selectedStrategy": "DELAYED_RETRY",
                          "rank": 1,
                          "rationale": "High risk environment requires caution",
                          "confidence": 0.85,
                          "riskLevel": "HIGH",
                          "affectedSegment": "Bank X + UPI",
                          "expectedRecoveryPercentage": 60.0,
                          "requiresHumanApproval": false,
                          "stoppingConditions": {
                            "maxRetryAttempts": 2,
                            "retryWindowMinutes": 20,
                            "backoffBaseSeconds": 30,
                            "stopOnSuccess": true,
                            "stopOnIncidentResolved": true,
                            "failureRateThreshold": 50.0,
                            "escalateOnFailureLimit": true,
                            "conditions": ["Max 2 retries"]
                          },
                          "alternatives": []
                        }
                        """;
            }
        };

        RecoveryStrategyService customService = new RecoveryStrategyService(
                incidentRepository, financialImpactService, incidentEvidenceService,
                incidentInvestigationRepository, incidentDiagnosisRepository,
                recoveryStrategyRepository, createCustomFactory(highRiskProvider), objectMapper, auditEventService
        );

        RecoveryStrategyResponse response = customService.generateStrategy(incident.getId());

        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.requiresHumanApproval()).isTrue();
    }

    @Test
    @DisplayName("Test 6: Financial bounding guarantees estimated recovery never exceeds authoritative recoverable amount")
    void testFinancialBounds_estimatedNeverExceedsAuthoritativeRecoverable() {
        Incident incident = createTestIncident(
                IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null,
                3.5, 78.5, 75.0
        );

        RecoveryStrategyResponse response = recoveryStrategyService.generateStrategy(incident.getId());

        assertThat(response.authoritativeRevenueAtRisk()).isNotNull();
        assertThat(response.authoritativeRecoverableAmount()).isNotNull();
        assertThat(response.expectedRecoveryPercentage()).isBetween(0.0, 100.0);
        assertThat(response.estimatedRecoveryAmount()).isNotNull();

        // Estimated recovery amount <= Authoritative recoverable revenue
        assertThat(response.estimatedRecoveryAmount())
                .isLessThanOrEqualTo(response.authoritativeRecoverableAmount());

        // Verify incident entity fields in DB were not overwritten
        Incident reloaded = incidentRepository.findById(incident.getId()).orElseThrow();
        assertThat(reloaded.getRevenueAtRisk()).isEqualByComparingTo(incident.getRevenueAtRisk());
    }

    @Test
    @DisplayName("Test 7: Malformed AI response triggers safe fallback to deterministic rules")
    void testInvalidAiResponse_fallsBackSafelyToDeterministicRules() {
        Incident incident = createTestIncident(
                IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null,
                3.5, 78.5, 75.0
        );

        AiProvider malformedProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "BROKEN_PROVIDER";
            }

            @Override
            public String getModelName() {
                return "broken-v1";
            }

            @Override
            public String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction) {
                return "{}";
            }

            @Override
            public String generateRecoveryStrategyJson(AiEvidenceDto evidence, String diagnosisSummary, String investigationSummary, String systemInstruction) {
                return "{ this is totally broken json }}}";
            }
        };

        RecoveryStrategyService customService = new RecoveryStrategyService(
                incidentRepository, financialImpactService, incidentEvidenceService,
                incidentInvestigationRepository, incidentDiagnosisRepository,
                recoveryStrategyRepository, createCustomFactory(malformedProvider), objectMapper, auditEventService
        );

        RecoveryStrategyResponse response = customService.generateStrategy(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo(StrategySource.FALLBACK);
        assertThat(response.selectedStrategy()).isEqualTo(RecoveryStrategyType.DELAYED_RETRY);
        assertThat(response.confidence()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("Test 8: AI Provider exception triggers safe fallback to deterministic rules")
    void testProviderUnavailable_fallsBackToDeterministicRules() {
        Incident incident = createTestIncident(
                IncidentType.MERCHANT_DEGRADATION, null, null, "merchant_hot",
                2.0, 65.0, 63.0
        );

        AiProvider throwingProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "CRASHED_PROVIDER";
            }

            @Override
            public String getModelName() {
                return "crashed-v1";
            }

            @Override
            public String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction) {
                return "{}";
            }

            @Override
            public String generateRecoveryStrategyJson(AiEvidenceDto evidence, String diagnosisSummary, String investigationSummary, String systemInstruction) {
                throw new RuntimeException("API connection timeout to upstream AI");
            }
        };

        RecoveryStrategyService customService = new RecoveryStrategyService(
                incidentRepository, financialImpactService, incidentEvidenceService,
                incidentInvestigationRepository, incidentDiagnosisRepository,
                recoveryStrategyRepository, createCustomFactory(throwingProvider), objectMapper, auditEventService
        );

        RecoveryStrategyResponse response = customService.generateStrategy(incident.getId());

        assertThat(response).isNotNull();
        assertThat(response.source()).isEqualTo(StrategySource.FALLBACK);
        assertThat(response.selectedStrategy()).isEqualTo(RecoveryStrategyType.HUMAN_REVIEW);
        assertThat(response.requiresHumanApproval()).isTrue();
    }

    @Test
    @DisplayName("Test 9: Strategy persistence stores record and supersedes old proposals")
    void testPersistence_storesAndRetrievesStrategy() {
        Incident incident = createTestIncident(
                IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null,
                3.5, 78.5, 75.0
        );

        // Run 1: first proposed strategy
        RecoveryStrategyResponse first = recoveryStrategyService.generateStrategy(incident.getId());
        assertThat(first.strategyId()).isNotNull();
        assertThat(first.status()).isEqualTo(StrategyStatus.PROPOSED);

        Optional<RecoveryStrategyResponse> retrieved = recoveryStrategyService.getLatestStrategy(incident.getId());
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().strategyId()).isEqualTo(first.strategyId());

        // Run 2: second strategy run supersedes the first
        RecoveryStrategyResponse second = recoveryStrategyService.generateStrategy(incident.getId());
        assertThat(second.strategyId()).isNotEqualTo(first.strategyId());
        assertThat(second.status()).isEqualTo(StrategyStatus.PROPOSED);

        // Verify first strategy in DB has SUPERSEDED status
        RecoveryStrategy firstEntity = recoveryStrategyRepository.findById(first.strategyId()).orElseThrow();
        assertThat(firstEntity.getStatus()).isEqualTo(StrategyStatus.SUPERSEDED);

        List<RecoveryStrategy> all = recoveryStrategyRepository.findAllByIncidentIdOrderByCreatedAtDesc(incident.getId());
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("Test 10: ALTERNATE_PAYMENT_METHOD strategy forces human approval")
    void testAlternatePaymentMethod_forcesHumanApproval() {
        Incident incident = createTestIncident(
                IncidentType.PAYMENT_DEGRADATION, "Bank X", PaymentMethod.UPI, null,
                3.5, 78.5, 75.0
        );

        AiProvider altMethodProvider = new AiProvider() {
            @Override
            public String getProviderName() {
                return "ALT_MOCK";
            }

            @Override
            public String getModelName() {
                return "alt-v1";
            }

            @Override
            public String generateDiagnosisJson(AiEvidenceDto evidence, String systemInstruction) {
                return "{}";
            }

            @Override
            public String generateRecoveryStrategyJson(AiEvidenceDto evidence, String diagnosisSummary, String investigationSummary, String systemInstruction) {
                return """
                        {
                          "selectedStrategy": "ALTERNATE_PAYMENT_METHOD",
                          "rank": 1,
                          "rationale": "Reroute UPI traffic to Cards rail",
                          "confidence": 0.85,
                          "riskLevel": "MEDIUM",
                          "affectedSegment": "Bank X + UPI",
                          "expectedRecoveryPercentage": 65.0,
                          "requiresHumanApproval": false,
                          "stoppingConditions": {
                            "maxRetryAttempts": 1,
                            "retryWindowMinutes": 15,
                            "backoffBaseSeconds": 0,
                            "stopOnSuccess": true,
                            "stopOnIncidentResolved": true,
                            "failureRateThreshold": 40.0,
                            "escalateOnFailureLimit": true,
                            "conditions": ["Reroute attempt 1"]
                          },
                          "alternatives": []
                        }
                        """;
            }
        };

        RecoveryStrategyService customService = new RecoveryStrategyService(
                incidentRepository, financialImpactService, incidentEvidenceService,
                incidentInvestigationRepository, incidentDiagnosisRepository,
                recoveryStrategyRepository, createCustomFactory(altMethodProvider), objectMapper, auditEventService
        );

        RecoveryStrategyResponse response = customService.generateStrategy(incident.getId());

        assertThat(response.selectedStrategy()).isEqualTo(RecoveryStrategyType.ALTERNATE_PAYMENT_METHOD);
        // Safety guardrail must force human approval for payment rerouting
        assertThat(response.requiresHumanApproval()).isTrue();
    }
}
