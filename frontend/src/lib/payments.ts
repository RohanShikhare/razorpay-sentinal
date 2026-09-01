import { apiFetch } from "./api";

export type PaymentMethod = "UPI" | "CARD" | "NETBANKING" | "WALLET";
export type PaymentStatus = "SUCCESS" | "FAILED" | "PENDING";
export type ErrorCode =
  | "NONE"
  | "BANK_TIMEOUT"
  | "INSUFFICIENT_FUNDS"
  | "NETWORK_ERROR"
  | "ISSUER_DECLINED"
  | "RATE_LIMIT"
  | "UNKNOWN";

export const BANKS = ["Bank A", "Bank B", "Bank C", "Bank X", "Bank Y"] as const;
export type Bank = (typeof BANKS)[number];

export interface Payment {
  id: number;
  merchantId: string;
  customerId: string;
  amount: string;
  currency: string;
  timestamp: string;
  paymentMethod: PaymentMethod;
  bank: string;
  status: PaymentStatus;
  errorCode: ErrorCode;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface PaymentFilters {
  status?: PaymentStatus;
  paymentMethod?: PaymentMethod;
  bank?: string;
  errorCode?: ErrorCode;
}

export interface GeneratePaymentsRequest {
  count: number;
  seed?: number;
}

export interface GeneratePaymentsResponse {
  requestedCount: number;
  generatedCount: number;
  windowStart: string;
  windowEnd: string;
  generatedAt: string;
}

export interface CountValueBreakdown {
  key: string;
  count: number;
  value: string;
}

export interface CountBreakdown {
  key: string;
  count: number;
}

export interface PaymentSummary {
  totalTransactions: number;
  successfulTransactions: number;
  failedTransactions: number;
  pendingTransactions: number;
  totalTransactionValue: string;
  successfulTransactionValue: string;
  failedTransactionValue: string;
  successRate: number;
  failureRate: number;
  byPaymentMethod: CountValueBreakdown[];
  byBank: CountValueBreakdown[];
  byErrorCode: CountBreakdown[];
}

export async function generatePayments(request: GeneratePaymentsRequest): Promise<GeneratePaymentsResponse> {
  return apiFetch<GeneratePaymentsResponse>("/api/v1/payments/generate", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export async function fetchPayments(
  page: number,
  size: number,
  filters: PaymentFilters
): Promise<PageResponse<Payment>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });
  if (filters.status) params.set("status", filters.status);
  if (filters.paymentMethod) params.set("paymentMethod", filters.paymentMethod);
  if (filters.bank) params.set("bank", filters.bank);
  if (filters.errorCode) params.set("errorCode", filters.errorCode);

  return apiFetch<PageResponse<Payment>>(`/api/v1/payments?${params.toString()}`);
}

export async function fetchPaymentSummary(): Promise<PaymentSummary> {
  return apiFetch<PaymentSummary>("/api/v1/payments/summary");
}
