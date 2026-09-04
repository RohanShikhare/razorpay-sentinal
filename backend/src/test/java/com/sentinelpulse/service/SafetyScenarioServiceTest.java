package com.sentinelpulse.service;

import com.sentinelpulse.dto.SafetyScenarioDto;
import com.sentinelpulse.dto.SafetyScenarioResult;
import com.sentinelpulse.safety.SafetyScenarioStatus;
import com.sentinelpulse.safety.SafetyScenarioType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test suite for {@link SafetyScenarioService} validating all 12 safety failure scenarios
 * and the 15 core safety invariants of Sentinel.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SafetyScenarioServiceTest {

    @Autowired
    private SafetyScenarioService safetyScenarioService;

    @Test
    @DisplayName("01. Scenario Definitions: All 12 controlled failure scenarios are registered")
    void testGetScenarioDefinitions() {
        List<SafetyScenarioDto> scenarios = safetyScenarioService.getScenarioDefinitions();
        assertThat(scenarios).hasSize(12);

        assertThat(scenarios)
                .extracting(SafetyScenarioDto::type)
                .containsExactlyInAnyOrder(
                        SafetyScenarioType.AI_PROVIDER_FAILURE,
                        SafetyScenarioType.AI_INVALID_RESPONSE,
                        SafetyScenarioType.AI_LOW_CONFIDENCE,
                        SafetyScenarioType.POLICY_REJECTION,
                        SafetyScenarioType.HUMAN_REVIEW_REQUIRED,
                        SafetyScenarioType.EXECUTION_FAILURE,
                        SafetyScenarioType.CIRCUIT_BREAKER,
                        SafetyScenarioType.INCIDENT_RESOLVED_DURING_EXECUTION,
                        SafetyScenarioType.STALE_STRATEGY,
                        SafetyScenarioType.IDEMPOTENT_EXECUTION,
                        SafetyScenarioType.AUDIT_TAMPER_DETECTION,
                        SafetyScenarioType.AUDIT_PERSISTENCE_FAILURE
                );
    }

    @Test
    @DisplayName("02. INVARIANT-001: AI Provider Outage fails closed without downstream recovery execution")
    void testAiProviderFailureScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.AI_PROVIDER_FAILURE);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.executionAllowed()).isFalse();
        assertThat(result.observedBehavior()).contains("FAILED");
        assertThat(result.auditVerificationValid()).isTrue();
    }

    @Test
    @DisplayName("03. INVARIANT-005: Schema-invalid/hallucinated AI response is rejected before policy or execution")
    void testAiInvalidResponseScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.AI_INVALID_RESPONSE);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.observedBehavior()).contains("INVALID_RESPONSE");
        assertThat(result.executionAllowed()).isFalse();
    }

    @Test
    @DisplayName("04. INVARIANT-005: Low confidence AI diagnosis enforces Human Review and blocks execution")
    void testAiLowConfidenceScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.AI_LOW_CONFIDENCE);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.policyDecision()).isEqualTo("HUMAN_REVIEW");
        assertThat(result.executionAllowed()).isFalse();
        assertThat(result.recoveryStatus()).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("05. INVARIANT-002: Policy rejection strictly prevents downstream execution")
    void testPolicyRejectionScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.POLICY_REJECTION);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.policyDecision()).isEqualTo("REJECTED");
        assertThat(result.executionAllowed()).isFalse();
        assertThat(result.recoveryStatus()).isEqualTo("BLOCKED");
        assertThat(result.stoppingReason()).isEqualTo("POLICY_BLOCKED");
    }

    @Test
    @DisplayName("06. INVARIANT-003: Human review decision prevents autonomous execution")
    void testHumanReviewRequiredScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.HUMAN_REVIEW_REQUIRED);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.policyDecision()).isEqualTo("HUMAN_REVIEW");
        assertThat(result.executionAllowed()).isFalse();
        assertThat(result.recoveryStatus()).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("07. INVARIANT-006 & INVARIANT-011: Execution failure respects bounded limits and revenue bounds")
    void testExecutionFailureScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.EXECUTION_FAILURE);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.executionId()).isNotNull();
        assertThat(result.recoveredAmount()).isNotNull();
        assertThat(result.auditVerificationValid()).isTrue();
    }

    @Test
    @DisplayName("08. INVARIANT-007: High failure rate trips circuit breaker immediately")
    void testCircuitBreakerScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.CIRCUIT_BREAKER);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.recoveryStatus()).isEqualTo("STOPPED");
        assertThat(result.stoppingReason()).isEqualTo("CIRCUIT_BREAKER_FAILURE_RATE");
    }

    @Test
    @DisplayName("09. INVARIANT-008: Resolved incident blocks recovery execution")
    void testIncidentResolvedScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.INCIDENT_RESOLVED_DURING_EXECUTION);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.executionAllowed()).isFalse();
        assertThat(result.recoveryStatus()).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("10. INVARIANT-009: Stale strategy triggers freshness guardrail and blocks execution")
    void testStaleStrategyScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.STALE_STRATEGY);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.policyDecision()).isEqualTo("HUMAN_REVIEW");
        assertThat(result.executionAllowed()).isFalse();
        assertThat(result.recoveryStatus()).isEqualTo("BLOCKED");
    }

    @Test
    @DisplayName("11. INVARIANT-010: Idempotent execution prevents duplicate retries and double-counting")
    void testIdempotentExecutionScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.IDEMPOTENT_EXECUTION);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.observedBehavior()).contains("Execution #1 ID");
        assertThat(result.observedBehavior()).contains("zero double-counting");
    }

    @Test
    @DisplayName("12. INVARIANT-013: Tampered audit event is detected by SHA-256 hash-chain verification")
    void testAuditTamperDetectionScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.AUDIT_TAMPER_DETECTION);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.auditVerificationValid()).isFalse();
        assertThat(result.observedBehavior()).contains("Broken event ID");
    }

    @Test
    @DisplayName("13. INVARIANT-012: Audit persistence failure is handled safely without crashing business flow")
    void testAuditPersistenceFailureScenario() {
        SafetyScenarioResult result = safetyScenarioService.runScenario(SafetyScenarioType.AUDIT_PERSISTENCE_FAILURE);
        assertThat(result.passed()).isTrue();
        assertThat(result.status()).isEqualTo(SafetyScenarioStatus.PASSED);
        assertThat(result.observedBehavior()).contains("fail-safe boundary");
    }

    @Test
    @DisplayName("14. Batch Lab Runner: All 12 scenarios execute and pass successfully")
    void testRunAllScenarios() {
        List<SafetyScenarioResult> results = safetyScenarioService.runAllScenarios();
        assertThat(results).hasSize(12);

        for (SafetyScenarioResult r : results) {
            assertThat(r.passed())
                    .as("Scenario %s must pass", r.scenario())
                    .isTrue();
            assertThat(r.status())
                    .as("Scenario %s status must be PASSED", r.scenario())
                    .isEqualTo(SafetyScenarioStatus.PASSED);
        }
    }
}
