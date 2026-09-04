import { apiFetch } from "./api";
import { API_BASE_URL } from "./config";

export type AuditEventType =
  | "INCIDENT_DETECTED"
  | "INCIDENT_UPDATED"
  | "INCIDENT_RESOLVED"
  | "FINANCIAL_IMPACT_CALCULATED"
  | "AI_DIAGNOSIS_REQUESTED"
  | "AI_DIAGNOSIS_COMPLETED"
  | "AI_DIAGNOSIS_FAILED"
  | "INVESTIGATION_REQUESTED"
  | "INVESTIGATION_COMPLETED"
  | "INVESTIGATION_FAILED"
  | "RECOVERY_STRATEGY_PROPOSED"
  | "RECOVERY_STRATEGY_REJECTED"
  | "POLICY_EVALUATED"
  | "POLICY_APPROVED"
  | "POLICY_APPROVED_WITH_LIMITS"
  | "POLICY_HUMAN_REVIEW"
  | "POLICY_REJECTED"
  | "RECOVERY_EXECUTION_REQUESTED"
  | "RECOVERY_EXECUTION_BLOCKED"
  | "RECOVERY_EXECUTION_STARTED"
  | "RECOVERY_ATTEMPTED"
  | "RECOVERY_SUCCEEDED"
  | "RECOVERY_FAILED"
  | "RECOVERY_STOPPED"
  | "RECOVERY_COMPLETED"
  | "EXECUTION_IDEMPOTENT_REUSE"
  | "SYSTEM_ERROR";

export type AuditEventCategory =
  | "DETECTION"
  | "FINANCIAL"
  | "DIAGNOSIS"
  | "INVESTIGATION"
  | "STRATEGY"
  | "POLICY"
  | "EXECUTION"
  | "SYSTEM";

export type AuditActorType =
  | "SYSTEM"
  | "AI"
  | "POLICY_ENGINE"
  | "RECOVERY_ENGINE"
  | "HUMAN_OPERATOR"
  | "USER";

export type AuditSeverity = "INFO" | "WARN" | "CRITICAL";

export type AuditStatus = "SUCCESS" | "FAILURE" | "BLOCKED" | "INFO";

export interface AuditEventDto {
  id: number;
  eventId: string;
  incidentId: number | null;
  executionId: number | null;
  strategyId: number | null;
  policyEvaluationId: number | null;
  eventType: AuditEventType;
  category: AuditEventCategory;
  actorType: AuditActorType;
  actorId: string;
  severity: AuditSeverity;
  status: AuditStatus;
  summary: string;
  reason: string | null;
  evidenceJson: string | null;
  metadataJson: string | null;
  timestamp: string;
  previousEventHash: string | null;
  eventHash: string;
}

export interface DecisionChainSummary {
  detected: boolean;
  diagnosed: boolean;
  investigated: boolean;
  strategyProposed: boolean;
  policyApproved: boolean;
  recoveryExecuted: boolean;
  recoveredAmount: number;
  currentStage: string;
}

export interface AuditTimelineResponse {
  incidentId: number | null;
  executionId: number | null;
  totalEvents: number;
  auditChainValid: boolean;
  firstEventTimestamp: string | null;
  lastEventTimestamp: string | null;
  decisionChainSummary: DecisionChainSummary | null;
  events: AuditEventDto[];
}

export interface AuditVerificationResponse {
  incidentId: number;
  valid: boolean;
  eventCount: number;
  firstEventTimestamp: string | null;
  lastEventTimestamp: string | null;
  brokenEventId: number | null;
  message: string;
}

/**
 * Fetches full chronological audit timeline for an incident.
 * GET /api/v1/incidents/{incidentId}/audit
 */
export async function fetchIncidentAuditTimeline(incidentId: number): Promise<AuditTimelineResponse> {
  return apiFetch<AuditTimelineResponse>(`/api/v1/incidents/${incidentId}/audit`);
}

/**
 * Verifies SHA-256 cryptographic hash-chain integrity for an incident.
 * GET /api/v1/incidents/{incidentId}/audit/verify
 */
export async function verifyIncidentAuditChain(incidentId: number): Promise<AuditVerificationResponse> {
  return apiFetch<AuditVerificationResponse>(`/api/v1/incidents/${incidentId}/audit/verify`);
}

/**
 * Fetches audit events scoped to a specific recovery execution.
 * GET /api/v1/recovery-executions/{executionId}/audit
 */
export async function fetchExecutionAuditTimeline(executionId: number): Promise<AuditTimelineResponse> {
  return apiFetch<AuditTimelineResponse>(`/api/v1/recovery-executions/${executionId}/audit`);
}
