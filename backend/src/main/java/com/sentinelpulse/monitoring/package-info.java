/**
 * Deterministic payment-failure monitoring and incident detection logic.
 *
 * {@link com.sentinelpulse.monitoring.PaymentStatistics} and
 * {@link com.sentinelpulse.monitoring.SegmentAnalyzer} are pure, side-effect
 * free statistics helpers; {@link com.sentinelpulse.monitoring.IncidentDetectionService}
 * is the orchestrating service that compares baseline vs current statistics,
 * evaluates thresholds, and persists {@link com.sentinelpulse.entity.Incident}
 * rows. This engine is fully deterministic - no AI, no LLM calls - and never
 * assigns a root cause; that is handled by the AI investigation engine.
 */
package com.sentinelpulse.monitoring;
