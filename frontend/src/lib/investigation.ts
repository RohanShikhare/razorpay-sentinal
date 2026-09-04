import { apiFetch } from "./api";
import { API_BASE_URL } from "./config";

export type InvestigationStatus = "REQUESTED" | "COMPLETED" | "FAILED" | "INVALID_RESPONSE";

export interface RootCauseHypothesis {
  hypothesis: string;
  confidence: number;
  supportingEvidence: string[];
  contradictingEvidence: string[];
  affectedSegment: string;
  verdict: "STRONGEST" | "LIKELY" | "UNLIKELY" | "REFUTED" | "INCONCLUSIVE" | string;
}

export interface IncidentInvestigationResponse {
  investigationId: number;
  incidentId: number;
  createdAt: string;
  completedAt: string;
  status: InvestigationStatus;
  provider: string;
  model: string;
  incidentType: string;
  summary: string;
  strongestRootCause: string;
  overallConfidence: number;
  affectedSegment: string;
  hypotheses: RootCauseHypothesis[];
  supportingEvidence: string[];
  contradictingEvidence: string[];
  reasoning: string;
  uncertainty: string;
  requiresHumanReview: boolean;
  statusReason?: string | null;
}

/**
 * Triggers an in-depth root-cause investigation on an incident.
 * POST /api/v1/incidents/{incidentId}/investigate
 */
export async function investigateIncident(incidentId: number): Promise<IncidentInvestigationResponse> {
  return apiFetch<IncidentInvestigationResponse>(`/api/v1/incidents/${incidentId}/investigate`, {
    method: "POST",
  });
}

/**
 * Fetches the latest existing root-cause investigation for an incident if available.
 * GET /api/v1/incidents/{incidentId}/investigation
 * Returns null if no investigation exists yet (404).
 */
export async function fetchLatestInvestigation(incidentId: number): Promise<IncidentInvestigationResponse | null> {
  try {
    const response = await fetch(`${API_BASE_URL}/api/v1/incidents/${incidentId}/investigation`, {
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

    return (await response.json()) as IncidentInvestigationResponse;
  } catch (err) {
    if (err instanceof Error && err.message.includes("404")) {
      return null;
    }
    throw err;
  }
}
