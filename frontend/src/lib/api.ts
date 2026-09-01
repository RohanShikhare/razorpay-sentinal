import { API_BASE_URL } from "./config";

/**
 * Thin API layer for talking to the Spring Boot backend.
 *
 * Each domain (health, payments, ...) gets its own set of functions here
 * rather than components calling fetch() directly. Payment-specific calls
 * live in lib/payments.ts; this file holds the shared fetch helper and the
 * health check used on the homepage.
 */

export interface HealthResponse {
  status: string;
  service: string;
  module: string;
  timestamp: string;
}

export type BackendConnection =
  | { state: "loading" }
  | { state: "online"; data: HealthResponse }
  | { state: "offline"; error: string };

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/v1/health`, {
    method: "GET",
    headers: { Accept: "application/json" },
    cache: "no-store",
  });

  if (!response.ok) {
    throw new Error(`Backend responded with status ${response.status}`);
  }

  return response.json() as Promise<HealthResponse>;
}

/**
 * Shared JSON fetch helper. Throws a readable Error on any non-2xx
 * response, using the backend's ErrorResponse.message when available.
 */
export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...init,
    headers: {
      Accept: "application/json",
      ...(init?.body ? { "Content-Type": "application/json" } : {}),
      ...init?.headers,
    },
    cache: "no-store",
  });

  if (!response.ok) {
    const message = await response
      .json()
      .then((body: { message?: string }) => body.message)
      .catch(() => undefined);
    throw new Error(message || `Request to ${path} failed with status ${response.status}`);
  }

  return response.json() as Promise<T>;
}
