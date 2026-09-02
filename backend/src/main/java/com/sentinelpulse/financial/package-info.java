/**
 * Deterministic revenue-at-risk and recoverability engine (Module 4).
 *
 * Builds on the revenue-at-risk figure {@link com.sentinelpulse.monitoring.IncidentDetectionService}
 * already computes and persists on each {@link com.sentinelpulse.entity.Incident}
 * - this package never redefines that number, only reads it, and adds a
 * recoverable/non-recoverable split plus per-segment financial breakdowns
 * derived live from the affected transaction population. Entirely
 * deterministic; no AI, no LLM calls.
 */
package com.sentinelpulse.financial;
