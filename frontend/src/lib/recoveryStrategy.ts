import { apiFetch } from "./api";
import { API_BASE_URL } from "./config";

export type RecoveryStrategyType =
  | "IMMEDIATE_RETRY"
  | "DELAYED_RETRY"
  | "ALTERNATE_PAYMENT_METHOD"
  | "RETRY_WITH_BACKOFF"
  | "MONITOR_ONLY"
  | "HUMAN_REVIEW"
  | "NO_ACTION";

export type StrategyStatus = "PROPOSED" | "REJECTED" | "SUPERSEDED" | "FAILED" | "INVALID_RESPONSE";

export type StrategySource = "DETERMINISTIC_RULES" | "AI_ASSISTED" | "FALLBACK";

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export interface StoppingConditions {
  maxRetryAttempts: number;
  retryWindowMinutes: number;
  backoffBaseSeconds: number;
  stopOnSuccess: boolean;
  stopOnIncidentResolved: boolean;
  failureRateThreshold: number;
  escalateOnFailureLimit: boolean;
  conditions: string[];
}

export interface AlternativeStrategy {
  strategy: RecoveryStrategyType;
  rank: number;
  reason: string;
  expectedRecoveryPercentage: number;
  riskLevel: RiskLevel;
}

export interface RecoveryStrategyResponse {
  strategyId: number;
  incidentId: number;
  investigationId: number | null;
  createdAt: string;
  status: StrategyStatus;
  selectedStrategy: RecoveryStrategyType;
  rank: number;
  rationale: string;
  confidence: number;
  riskLevel: RiskLevel;
  affectedSegment: string;
  authoritativeRevenueAtRisk: number;
  authoritativeRecoverableAmount: number;
  expectedRecoveryPercentage: number;
  estimatedRecoveryAmount: number;
  estimatedAffectedTransactionCount: number;
  stoppingConditions: StoppingConditions;
  requiresHumanApproval: boolean;
  alternatives: AlternativeStrategy[];
  source: StrategySource;
  advisoryDisclaimer: string;
  statusReason?: string | null;
}

/**
 * Generates and proposes a bounded recovery strategy for an incident.
 * POST /api/v1/incidents/{incidentId}/recovery-strategy
 */
export async function generateRecoveryStrategy(incidentId: number): Promise<RecoveryStrategyResponse> {
  return apiFetch<RecoveryStrategyResponse>(`/api/v1/incidents/${incidentId}/recovery-strategy`, {
    method: "POST",
  });
}

/**
 * Fetches the latest existing recovery strategy proposal for an incident if available.
 * GET /api/v1/incidents/{incidentId}/recovery-strategy
 * Returns null if no strategy exists yet (404).
 */
export async function fetchLatestRecoveryStrategy(incidentId: number): Promise<RecoveryStrategyResponse | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/incidents/${incidentId}/recovery-strategy`, {
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

    return (await response.json()) as RecoveryStrategyResponse;
  } catch (err) {
    if (err instanceof Error && err.message.includes("404")) {
      return null;
    }
    throw err;
  }
}
