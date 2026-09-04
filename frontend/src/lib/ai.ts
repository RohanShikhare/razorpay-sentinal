import { apiFetch } from "./api";
import { API_BASE_URL } from "./config";

export type ControlledAction =
  | "IMMEDIATE_RETRY"
  | "DELAYED_RETRY"
  | "ALTERNATE_PAYMENT_METHOD"
  | "MONITOR"
  | "HUMAN_REVIEW"
  | "NO_ACTION";

export type RiskLevel = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";

export type DiagnosisStatus = "REQUESTED" | "COMPLETED" | "FAILED" | "INVALID_RESPONSE";

export interface AiDiagnosisResponse {
  diagnosisId: number;
  incidentId: number;
  timestamp: string;
  provider: string;
  model: string;
  incidentType: string;
  rootCause: string;
  reasoning: string;
  confidence: number;
  evidence: string[];
  affectedSegment: string;
  recommendedAction: ControlledAction;
  estimatedRecoveryPercentage: number;
  riskLevel: RiskLevel;
  requiresHumanApproval: boolean;
  status: DiagnosisStatus;
  statusReason?: string | null;
}

/**
 * Triggers an AI diagnostic analysis on an incident.
 * POST /api/v1/incidents/{incidentId}/diagnose
 */
export async function diagnoseIncident(incidentId: number): Promise<AiDiagnosisResponse> {
  return apiFetch<AiDiagnosisResponse>(`/api/v1/incidents/${incidentId}/diagnose`, {
    method: "POST",
  });
}

/**
 * Fetches the latest existing AI diagnosis for an incident if available.
 * GET /api/v1/incidents/{incidentId}/diagnosis
 * Returns null if no diagnosis exists yet (404).
 */
export async function fetchLatestDiagnosis(incidentId: number): Promise<AiDiagnosisResponse | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/incidents/${incidentId}/diagnosis`, {
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

    return (await response.json()) as AiDiagnosisResponse;
  } catch (err) {
    if (err instanceof Error && err.message.includes("404")) {
      return null;
    }
    throw err;
  }
}
