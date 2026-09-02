import { apiFetch } from "./api";
import type { PaymentMethod } from "./payments";

export type IncidentType = "PAYMENT_DEGRADATION" | "MERCHANT_DEGRADATION" | "SYSTEM_DEGRADATION";
export type IncidentSeverity = "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
export type IncidentStatus =
  | "DETECTED"
  | "INVESTIGATING"
  | "RECOVERY_PENDING"
  | "RECOVERING"
  | "RESOLVED"
  | "ESCALATED";

export interface SegmentBreakdown {
  dimension: "BANK" | "PAYMENT_METHOD" | "BANK_PAYMENT_METHOD" | "MERCHANT";
  label: string;
  baselineTransactions: number;
  currentTransactions: number;
  baselineFailureRate: number;
  currentFailureRate: number;
  failureRateIncrease: number;
}

export interface DetectionResult {
  incidentDetected: boolean;
  incidentId: number | null;
  incidentType: IncidentType | null;
  severity: IncidentSeverity | null;
  baselineFailureRate: number;
  currentFailureRate: number;
  failureRateIncrease: number;
  relativeIncreasePercent: number | null;
  affectedTransactions: number;
  affectedTransactionValue: string;
  failedTransactionValue: string;
  revenueAtRisk: string;
  confidence: number | null;
  monitoringWindowMinutes: number;
  targetBank: string | null;
  targetPaymentMethod: PaymentMethod | null;
  targetMerchantId: string | null;
  topAffectedSegments: SegmentBreakdown[];
  timestamp: string;
  message: string | null;
}

export interface MonitoringSummary {
  paymentHealth: string;
  successRate: number;
  failureRate: number;
  baselineFailureRate: number;
  failureRateChange: number;
  revenueAtRisk: string;
  estimatedRecoverableRevenue: string;
  affectedTransactionValue: string;
  activeIncidents: number;
  affectedTransactions: number;
}

export interface Incident {
  id: number;
  type: IncidentType;
  severity: IncidentSeverity;
  status: IncidentStatus;
  detectedAt: string;
  startTime: string;
  endTime: string | null;
  affectedTransactions: number;
  affectedTransactionValue: string;
  failedTransactionValue: string;
  revenueAtRisk: string;
  confidence: number;
  rootCause: string | null;
  baselineFailureRate: number;
  currentFailureRate: number;
  failureRateIncrease: number;
  targetBank: string | null;
  targetPaymentMethod: PaymentMethod | null;
  targetMerchantId: string | null;
  monitoringWindowMinutes: number;
}

export interface IncidentDetail {
  incident: Incident;
  topAffectedSegments: SegmentBreakdown[];
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function runDetection(windowMinutes?: number): Promise<DetectionResult> {
  return apiFetch<DetectionResult>("/api/v1/monitoring/detect", {
    method: "POST",
    body: JSON.stringify(windowMinutes ? { windowMinutes } : {}),
  });
}

export async function fetchMonitoringSummary(): Promise<MonitoringSummary> {
  return apiFetch<MonitoringSummary>("/api/v1/monitoring/summary");
}

export async function fetchIncidents(page = 0, size = 20): Promise<PageResponse<Incident>> {
  return apiFetch<PageResponse<Incident>>(`/api/v1/incidents?page=${page}&size=${size}`);
}

export async function fetchIncidentDetail(id: number): Promise<IncidentDetail> {
  return apiFetch<IncidentDetail>(`/api/v1/incidents/${id}`);
}
