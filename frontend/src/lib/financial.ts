import { apiFetch } from "./api";

export interface FinancialImpact {
  incidentId: number;
  affectedTransactions: number;
  affectedTransactionValue: string;
  failedTransactionValue: string;
  revenueAtRisk: string;
  estimatedRecoverableRevenue: string;
  nonRecoverableRevenue: string;
  averageTransactionValue: string;
  largestAffectedTransaction: string;
  recoverablePercentage: number;
}

export interface FinancialSegment {
  dimension: "BANK" | "PAYMENT_METHOD" | "ERROR_CODE" | "MERCHANT";
  segment: string;
  transactionCount: number;
  transactionValue: string;
  failedValue: string;
  revenueAtRisk: string;
  estimatedRecoverableRevenue: string;
}

export interface FinancialBreakdown {
  incidentId: number;
  byBank: FinancialSegment[];
  byPaymentMethod: FinancialSegment[];
  byErrorCode: FinancialSegment[];
  byMerchant: FinancialSegment[];
}

export async function fetchFinancialImpact(incidentId: number): Promise<FinancialImpact> {
  return apiFetch<FinancialImpact>(`/api/v1/incidents/${incidentId}/financial-impact`);
}

export async function fetchFinancialBreakdown(incidentId: number): Promise<FinancialBreakdown> {
  return apiFetch<FinancialBreakdown>(`/api/v1/incidents/${incidentId}/financial-impact/breakdown`);
}
