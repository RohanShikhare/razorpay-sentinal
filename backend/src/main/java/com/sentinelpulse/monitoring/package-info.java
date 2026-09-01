/**
 * Deterministic payment-failure monitoring and incident detection logic.
 *
 * Implemented in Module 3 (Monitoring & Incident Detection Engine):
 * {@link com.sentinelpulse.monitoring.PaymentStatistics} and
 * {@link com.sentinelpulse.monitoring.SegmentAnalyzer} are pure, side-effect
 * free statistics helpers; {@link com.sentinelpulse.monitoring.IncidentDetectionService}
 * is the orchestrating service that compares baseline vs current statistics,
 * evaluates thresholds, and persists {@link com.sentinelpulse.entity.Incident}
 * rows. This engine is fully deterministic - no AI, no LLM calls - and never
 * assigns a root cause; that is a future AI investigation module's job.
 */
package com.sentinelpulse.monitoring;
