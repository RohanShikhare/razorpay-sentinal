import { apiFetch } from "./api";
import { API_BASE_URL } from "./config";
import { RiskLevel } from "./recoveryStrategy";

export type PolicyDecision =
  | "APPROVED"
  | "APPROVED_WITH_LIMITS"
  | "HUMAN_REVIEW"
  | "REJECTED";

export type PolicyCheckResult = "PASS" | "WARN" | "FAIL";
export type PolicyCheckSeverity = "CRITICAL" | "MAJOR" | "MINOR" | "INFO";

export interface PolicyCheck {
  policyId: string;
  policyName: string;
  result: PolicyCheckResult;
  severity: PolicyCheckSeverity;
  reason: string;
  observedValue: string;
  allowedValue: string;
}

export interface AppliedLimit {
  parameter: string;
  proposedValue: string;
  enforcedValue: string;
  reason: string;
}

export interface PolicyEvaluationResponse {
  evaluationId: number;
  incidentId: number;
  strategyId: number;
  evaluatedAt: string;
  evaluatorVersion: string;
  decision: PolicyDecision;
  overallAllowed: boolean;
  executionAllowed: boolean;
  requiredHumanApproval: boolean;
  riskLevel: RiskLevel;
  policyScore: number;
  summary: string;
  executionBlockedReason: string | null;
  nextStep: string;
  policyChecks: PolicyCheck[];
  appliedLimits: AppliedLimit[];
  violations: string[];
  warnings: string[];
  advisoryDisclaimer: string;
}

/**
 * Evaluates the latest proposed recovery strategy against deterministic safety policies and guardrails.
 * POST /api/v1/incidents/{incidentId}/policy-evaluate
 */
export async function evaluatePolicy(incidentId: number): Promise<PolicyEvaluationResponse> {
  return apiFetch<PolicyEvaluationResponse>(`/api/v1/incidents/${incidentId}/policy-evaluate`, {
    method: "POST",
  });
}

/**
 * Fetches the latest persisted policy evaluation for an incident if available.
 * GET /api/v1/incidents/{incidentId}/policy-evaluation
 * Returns null if no policy evaluation exists yet (404).
 */
export async function fetchLatestPolicyEvaluation(incidentId: number): Promise<PolicyEvaluationResponse | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/incidents/${incidentId}/policy-evaluation`, {
      method: "GET",
      headers: { Accept: "application/json" },
      cache: "no-store",
    });

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      const message = await response
        .json()
        .then((body: { message?: string }) => body.message)
        .catch(() => undefined);
      throw new Error(message || `Request failed with status ${response.status}`);
    }

    return (await response.json()) as PolicyEvaluationResponse;
  } catch (err) {
    if (err instanceof Error && err.message.includes("404")) {
      return null;
    }
    throw err;
  }
}
