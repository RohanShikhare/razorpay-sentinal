package com.sentinelpulse.entity;

/**
 * How severe a detected incident is, based on how far the failure rate has
 * moved from baseline. See {@code MonitoringProperties.Thresholds} for the
 * configurable percentage-point boundaries between levels.
 */
public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
