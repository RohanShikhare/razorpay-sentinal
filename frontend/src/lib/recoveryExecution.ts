import { apiFetch } from "./api";
import { API_BASE_URL } from "./config";
import { RecoveryStrategyType } from "./recoveryStrategy";

export type ExecutionStatus =
  | "REQUESTED"
  | "BLOCKED"
  | "RUNNING"
  | "COMPLETED"
  | "PARTIALLY_COMPLETED"
  | "STOPPED"
  | "FAILED";

export type RecoveryOutcome =
  | "RECOVERED"
  | "FAILED"
  | "SKIPPED"
  | "ALREADY_SUCCESSFUL"
  | "STOPPED_BY_LIMIT"
  | "STOPPED_BY_CIRCUIT_BREAKER";

export type StoppingReason =
  | "COMPLETED_ALL_ELIGIBLE"
  | "MAX_ATTEMPTS_REACHED"
  | "RETRY_WINDOW_EXCEEDED"
  | "CIRCUIT_BREAKER_FAILURE_RATE"
  | "INCIDENT_RESOLVED"
  | "HUMAN_APPROVAL_REQUIRED"
  | "POLICY_BLOCKED"
  | "NO_ACTION_REQUIRED";

export interface RecoveryExecutionAttemptDto {
  attemptId: number;
  paymentId: number;
  attemptNumber: number;
  attemptedAt: string;
  outcome: RecoveryOutcome;
  amount: number;
  failureReason: string | null;
  strategyType: RecoveryStrategyType;
  simulatedDelaySeconds: number | null;
}

export interface RecoveryMetricsDto {
  recoveryRate: number;
  financialRecoveryRate: number;
  averageAttemptsPerRecovery: number;
  executionDurationSeconds: number;
}

export interface RecoveryExecutionResponse {
  executionId: number;
  incidentId: number;
  strategyId: number;
  policyEvaluationId: number;
  startedAt: string;
  completedAt: string | null;
  status: ExecutionStatus;
  strategyType: RecoveryStrategyType;
  eligibleTransactions: number;
  attemptedTransactions: number;
  successfulRecoveries: number;
  failedRecoveries: number;
  skippedTransactions: number;
  recoveredAmount: number;
  attemptedAmount: number;
  remainingRecoverableAmount: number;
  authoritativeRevenueAtRisk: number;
  authoritativeRecoverableAmount: number;
  expectedRecoveryPercentage: number | null;
  metrics: RecoveryMetricsDto;
  stoppingReason: StoppingReason;
  stoppingDetails: string;
  executionAllowed: boolean;
  appliedRetryLimit: number | null;
  appliedRetryWindow: number | null;
  appliedBackoff: number | null;
  simulationVersion: string;
  attempts: RecoveryExecutionAttemptDto[];
  simulationDisclaimer: string;
}

/**
 * Triggers bounded recovery execution simulation for an incident obeying Policy Gate limits.
 * POST /api/v1/incidents/{incidentId}/recovery-execute
 */
export async function executeRecovery(incidentId: number): Promise<RecoveryExecutionResponse> {
  return apiFetch<RecoveryExecutionResponse>(`/api/v1/incidents/${incidentId}/recovery-execute`, {
    method: "POST",
  });
}

/**
 * Fetches the latest persisted recovery execution for an incident if available.
 * GET /api/v1/incidents/{incidentId}/recovery-execution
 * Returns null if no execution exists yet (404).
 */
export async function fetchLatestRecoveryExecution(incidentId: number): Promise<RecoveryExecutionResponse | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/incidents/${incidentId}/recovery-execution`, {
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

    return (await response.json()) as RecoveryExecutionResponse;
  } catch (err) {
    if (err instanceof Error && err.message.includes("404")) {
      return null;
    }
    throw err;
  }
}
