package com.sentinelpulse.service;

import com.sentinelpulse.dto.EvaluationMetricsDto.DetectionMetricsDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.DiagnosisMetricsDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.FinancialMetricsDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.PolicyMetricsEvaluationDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.RecoveryMetricsEvaluationDto;
import com.sentinelpulse.dto.EvaluationMetricsDto.SafetyMetricsEvaluationDto;
import com.sentinelpulse.dto.EvaluationRunRequest;
import com.sentinelpulse.dto.EvaluationRunResponse;
import com.sentinelpulse.dto.EvaluationScenarioDescriptorDto;
import com.sentinelpulse.dto.IncidentEvaluationResultDto;
import com.sentinelpulse.evaluation.EvaluationScenarioType;
import com.sentinelpulse.policy.PolicyDecision;
import com.sentinelpulse.recovery.ExecutionStatus;
import com.sentinelpulse.repository.IncidentRepository;
import com.sentinelpulse.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class EvaluationServiceTest {

    @Autowired
    private EvaluationService evaluationService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @BeforeEach
    void setUp() {
        incidentRepository.deleteAll();
        paymentRepository.deleteAll();
    }

    @Test
    @DisplayName("Evaluation Engine: Available evaluation scenarios are complete and describe ground truth")
    void testGetAvailableScenarios() {
        List<EvaluationScenarioDescriptorDto> scenarios = evaluationService.getAvailableScenarios();

        assertThat(scenarios).isNotNull().hasSize(4);
        assertThat(scenarios)
                .extracting(EvaluationScenarioDescriptorDto::scenario)
                .containsExactlyInAnyOrder(
                        EvaluationScenarioType.BANK_X_UPI_DEGRADATION,
                        EvaluationScenarioType.MERCHANT_SPECIFIC_DEGRADATION,
                        EvaluationScenarioType.NORMAL_RANDOM_FAILURES,
                        EvaluationScenarioType.GATEWAY_TIMEOUT_DEGRADATION
                );
    }

    @Test
    @DisplayName("Evaluation Engine: Batch evaluation runs deterministically across multi-scenarios")
    void testRunEvaluation_deterministicAndComplete() {
        EvaluationRunRequest request = new EvaluationRunRequest(8, 42L, true, null);
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        assertThat(response).isNotNull();
        assertThat(response.runId()).startsWith("EVAL-");
        assertThat(response.evaluatorVersion()).isEqualTo("EVAL_V1");
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.scenarioCount()).isEqualTo(8);
        assertThat(response.completedScenarios()).isGreaterThanOrEqualTo(7);
        assertThat(response.incidents()).hasSize(8);
    }

    @Test
    @DisplayName("INVARIANT: Financial recovery never exceeds authoritative recoverable revenue")
    void testFinancialMetrics_mathBounds() {
        EvaluationRunRequest request = new EvaluationRunRequest(4, 100L, true, null);
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        FinancialMetricsDto financial = response.financial();
        assertThat(financial).isNotNull();

        assertThat(financial.totalRevenueAtRisk()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(financial.totalRecoverableRevenue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
        assertThat(financial.totalRecoveredRevenue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);

        // Invariant: recovered <= recoverable
        assertThat(financial.totalRecoveredRevenue())
                .isLessThanOrEqualTo(financial.totalRecoverableRevenue());

        // Invariant: unrecovered = max(0, recoverable - recovered)
        BigDecimal expectedUnrecovered = financial.totalRecoverableRevenue()
                .subtract(financial.totalRecoveredRevenue()).max(BigDecimal.ZERO);
        assertThat(financial.totalUnrecoveredRevenue()).isEqualByComparingTo(expectedUnrecovered);

        // Recovery rate within 0 - 100%
        assertThat(financial.financialRecoveryRate()).isBetween(0.0, 100.0);
    }

    @Test
    @DisplayName("Evaluation Engine: Detection metrics accurately capture confusion matrix and F1 score")
    void testDetectionMetrics_confusionMatrix() {
        EvaluationRunRequest request = new EvaluationRunRequest(8, 42L, true, null);
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        DetectionMetricsDto detection = response.detection();
        assertThat(detection).isNotNull();
        assertThat(detection.totalScenariosEvaluated()).isEqualTo(8);
        assertThat(detection.truePositives()).isGreaterThan(0);
        assertThat(detection.precision()).isBetween(0.0, 100.0);
        assertThat(detection.recall()).isBetween(0.0, 100.0);
        assertThat(detection.f1Score()).isBetween(0.0, 100.0);
        assertThat(detection.averageDetectionLatencyMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("Evaluation Engine: Diagnostic accuracy evaluates root-cause keywords against ground truth")
    void testDiagnosisMetrics_accuracyAndGroundTruth() {
        EvaluationRunRequest request = new EvaluationRunRequest(8, 42L, true, null);
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        DiagnosisMetricsDto diagnosis = response.diagnosis();
        assertThat(diagnosis).isNotNull();
        assertThat(diagnosis.totalDiagnosesEvaluated()).isGreaterThan(0);
        assertThat(diagnosis.diagnosisAccuracy()).isGreaterThan(0.0);
        assertThat(diagnosis.averageConfidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("Evaluation Engine: Policy compliance verifies zero unauthorized recovery bypasses")
    void testPolicyCompliance_andSafetyScore() {
        EvaluationRunRequest request = new EvaluationRunRequest(6, 42L, true, null);
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        PolicyMetricsEvaluationDto policy = response.policy();
        assertThat(policy).isNotNull();
        assertThat(policy.totalPolicyEvaluations()).isGreaterThan(0);
        assertThat(policy.policyComplianceRate()).isEqualTo(100.0);

        SafetyMetricsEvaluationDto safety = response.safety();
        assertThat(safety).isNotNull();
        assertThat(safety.safetyScore()).isEqualTo(100);
        assertThat(safety.policyBypasses()).isZero();
        assertThat(safety.unauthorizedExecutions()).isZero();
        assertThat(safety.financialBoundViolations()).isZero();
        assertThat(safety.duplicateRecoveries()).isZero();
        assertThat(safety.auditIntegrityFailures()).isZero();
    }

    @Test
    @DisplayName("Evaluation Engine: Baseline comparisons compute positive uplift over No Recovery")
    void testBaselineComparison_upliftCalculation() {
        EvaluationRunRequest request = new EvaluationRunRequest(6, 42L, true, null);
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        FinancialMetricsDto financial = response.financial();
        assertThat(financial.baselineNoRecoveryAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(financial.recoveryUpliftVsNoRecovery())
                .isEqualByComparingTo(financial.totalRecoveredRevenue());
        assertThat(financial.recoveryUpliftVsNaiveRetry()).isNotNull();
    }

    @Test
    @DisplayName("Evaluation Engine: Seed reproducibility produces identical benchmark results")
    void testSeedReproducibility() {
        EvaluationRunRequest req1 = new EvaluationRunRequest(4, 777L, true, null);
        EvaluationRunResponse resp1 = evaluationService.runEvaluation(req1);

        incidentRepository.deleteAll();
        paymentRepository.deleteAll();

        EvaluationRunRequest req2 = new EvaluationRunRequest(4, 777L, true, null);
        EvaluationRunResponse resp2 = evaluationService.runEvaluation(req2);

        assertThat(resp1.financial().totalRevenueAtRisk())
                .isEqualByComparingTo(resp2.financial().totalRevenueAtRisk());
        assertThat(resp1.financial().totalRecoverableRevenue())
                .isEqualByComparingTo(resp2.financial().totalRecoverableRevenue());
        assertThat(resp1.financial().totalRecoveredRevenue())
                .isEqualByComparingTo(resp2.financial().totalRecoveredRevenue());
        assertThat(resp1.detection().f1Score())
                .isEqualTo(resp2.detection().f1Score());
        assertThat(resp1.safety().safetyScore())
                .isEqualTo(resp2.safety().safetyScore());
    }

    @Test
    @DisplayName("Evaluation Engine: Merchant degradation blocks autonomous retries to protect merchant")
    void testMerchantProtection_blocksAutomatedExecution() {
        EvaluationRunRequest request = new EvaluationRunRequest(
                1, 42L, true, List.of(EvaluationScenarioType.MERCHANT_SPECIFIC_DEGRADATION)
        );
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        assertThat(response.incidents()).hasSize(1);
        IncidentEvaluationResultDto merchantResult = response.incidents().get(0);

        assertThat(merchantResult.scenarioType()).isEqualTo(EvaluationScenarioType.MERCHANT_SPECIFIC_DEGRADATION);
        assertThat(merchantResult.policyDecision()).isIn(PolicyDecision.HUMAN_REVIEW, PolicyDecision.REJECTED);
        assertThat(merchantResult.executionAllowed()).isFalse();
        assertThat(merchantResult.executionStatus()).isEqualTo(ExecutionStatus.BLOCKED);
    }

    @Test
    @DisplayName("Evaluation Engine: Normal random noise avoids false positive alerts")
    void testNegativeScenario_normalNoiseAvoidsFalsePositive() {
        EvaluationRunRequest request = new EvaluationRunRequest(
                1, 42L, true, List.of(EvaluationScenarioType.NORMAL_RANDOM_FAILURES)
        );
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        assertThat(response.incidents()).hasSize(1);
        IncidentEvaluationResultDto noiseResult = response.incidents().get(0);

        assertThat(noiseResult.scenarioType()).isEqualTo(EvaluationScenarioType.NORMAL_RANDOM_FAILURES);
        assertThat(noiseResult.detected()).isFalse();
        assertThat(noiseResult.recoveredRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Evaluation Engine: Cryptographic audit verification passes for evaluated incidents")
    void testAuditIntegrity_verifiedAcrossEvaluation() {
        EvaluationRunRequest request = new EvaluationRunRequest(4, 42L, true, null);
        EvaluationRunResponse response = evaluationService.runEvaluation(request);

        for (IncidentEvaluationResultDto inc : response.incidents()) {
            assertThat(inc.auditVerified()).isTrue();
        }
    }
}
