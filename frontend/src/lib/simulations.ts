import { apiFetch } from "./api";
import type { PaymentMethod } from "./payments";

export type SimulationScenario =
  | "BANK_X_UPI_DEGRADATION"
  | "MERCHANT_SPECIFIC_DEGRADATION"
  | "NORMAL_RANDOM_FAILURES";

export const SCENARIOS: { value: SimulationScenario; label: string; description: string }[] = [
  {
    value: "BANK_X_UPI_DEGRADATION",
    label: "Bank X UPI Degradation",
    description: "Primary demo scenario — Bank X's UPI traffic develops a sharp, timeout-heavy failure spike.",
  },
  {
    value: "MERCHANT_SPECIFIC_DEGRADATION",
    label: "Merchant-Specific Degradation",
    description: "One high-volume merchant develops elevated failures while banks stay uninvolved.",
  },
  {
    value: "NORMAL_RANDOM_FAILURES",
    label: "Normal Random Failures",
    description: "Ordinary background noise — a small, broadly-spread increase in failures.",
  },
];

export interface SimulationRunResponse {
  scenario: SimulationScenario;
  affectedTransactions: number;
  modifiedTransactions: number;
  targetBank: string | null;
  targetPaymentMethod: PaymentMethod | null;
  targetMerchantId: string | null;
  failureRateBefore: number;
  failureRateAfter: number;
  simulationTimestamp: string;
  previousSimulationReset: boolean;
  message: string | null;
}

export interface SimulationStatusResponse {
  active: boolean;
  scenario: SimulationScenario | null;
  startedAt: string | null;
  affectedTransactions: number;
  modifiedTransactions: number;
  targetBank: string | null;
  targetPaymentMethod: PaymentMethod | null;
  targetMerchantId: string | null;
  failureRateBefore: number;
  failureRateAfter: number;
}

export interface SimulationResetResponse {
  reset: boolean;
  restoredTransactions: number;
  resetTimestamp: string;
  message: string | null;
}

export async function runSimulation(scenario: SimulationScenario): Promise<SimulationRunResponse> {
  return apiFetch<SimulationRunResponse>("/api/v1/simulations/run", {
    method: "POST",
    body: JSON.stringify({ scenario }),
  });
}

export async function resetSimulation(): Promise<SimulationResetResponse> {
  return apiFetch<SimulationResetResponse>("/api/v1/simulations/reset", { method: "POST" });
}

export async function fetchSimulationStatus(): Promise<SimulationStatusResponse> {
  return apiFetch<SimulationStatusResponse>("/api/v1/simulations/status");
}
