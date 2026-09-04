import { apiFetch } from "./api";

export type SafetyScenarioType =
  | "AI_PROVIDER_FAILURE"
  | "AI_INVALID_RESPONSE"
  | "AI_LOW_CONFIDENCE"
  | "POLICY_REJECTION"
  | "HUMAN_REVIEW_REQUIRED"
  | "EXECUTION_FAILURE"
  | "CIRCUIT_BREAKER"
  | "INCIDENT_RESOLVED_DURING_EXECUTION"
  | "STALE_STRATEGY"
  | "IDEMPOTENT_EXECUTION"
  | "AUDIT_TAMPER_DETECTION"
  | "AUDIT_PERSISTENCE_FAILURE";

export type SafetyScenarioStatus = "PASSED" | "FAILED" | "ERROR";

export interface SafetyScenarioDto {
  type: SafetyScenarioType;
  name: string;
  category: string;
  description: string;
  safetyProperty: string;
  expectedBehavior: string;
}

export interface SafetyScenarioResult {
  scenario: SafetyScenarioType;
  status: SafetyScenarioStatus;
  passed: boolean;
  name: string;
  category: string;
  expectedBehavior: string;
  observedBehavior: string;
  safetyProperty: string;
  incidentId: number | null;
  executionId: number | null;
  policyDecision: string | null;
  executionAllowed: boolean | null;
  recoveryStatus: string | null;
  recoveredAmount: number;
  stoppingReason: string | null;
  auditEventCount: number | null;
  auditVerificationValid: boolean | null;
  details: string;
  executedAt: string;
}

/**
 * Fetch all available controlled safety failure scenarios.
 */
export async function getSafetyScenarios(): Promise<SafetyScenarioDto[]> {
  return apiFetch<SafetyScenarioDto[]>("/api/v1/safety/scenarios");
}

/**
 * Runs a single controlled safety scenario.
 */
export async function runSafetyScenario(
  scenario: SafetyScenarioType
): Promise<SafetyScenarioResult> {
  return apiFetch<SafetyScenarioResult>(`/api/v1/safety/scenarios/${scenario}/run`, {
    method: "POST",
  });
}

/**
 * Runs all 12 controlled safety scenarios in batch.
 */
export async function runAllSafetyScenarios(): Promise<SafetyScenarioResult[]> {
  return apiFetch<SafetyScenarioResult[]>("/api/v1/safety/scenarios/run-all", {
    method: "POST",
  });
}
