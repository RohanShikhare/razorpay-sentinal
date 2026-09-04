package com.sentinelpulse.dto;

import java.util.List;

/**
 * Bounded stopping conditions that govern any simulated or proposed recovery strategy.
 * Prevents cascading retries or indefinite processing loops.
 */
public record StoppingConditionsDto(
        int maxRetryAttempts,
        int retryWindowMinutes,
        int backoffBaseSeconds,
        boolean stopOnSuccess,
        boolean stopOnIncidentResolved,
        double failureRateThreshold,
        boolean escalateOnFailureLimit,
        List<String> conditions
) {
    public static StoppingConditionsDto standardRetry(int maxAttempts, int windowMinutes, int backoffSeconds) {
        return new StoppingConditionsDto(
                maxAttempts,
                windowMinutes,
                backoffSeconds,
                true,
                true,
                50.0,
                true,
                List.of(
                        String.format("Maximum %d retry attempts per affected payment", maxAttempts),
                        String.format("Retry window capped at %d minutes", windowMinutes),
                        String.format("Exponential backoff starting at %d seconds", backoffSeconds),
                        "Stop immediately after successful payment settlement",
                        "Stop if incident is marked resolved",
                        "Abort if downstream retry failure rate exceeds 50.0%",
                        "Escalate to human review if attempts exceed limit without resolution"
                )
        );
    }

    public static StoppingConditionsDto alternatePayment(int windowMinutes) {
        return new StoppingConditionsDto(
                1,
                windowMinutes,
                0,
                true,
                true,
                40.0,
                true,
                List.of(
                        "Single rerouting attempt per affected payment to alternative healthy rail",
                        String.format("Rerouting window capped at %d minutes", windowMinutes),
                        "Stop immediately after transaction success on alternate rail",
                        "Stop if incident is marked resolved",
                        "Abort rerouting if alternative rail error rate exceeds 40.0%",
                        "Escalate to human review if user declines alternate payment method"
                )
        );
    }

    public static StoppingConditionsDto passiveMonitoring() {
        return new StoppingConditionsDto(
                0,
                60,
                0,
                false,
                true,
                0.0,
                true,
                List.of(
                        "Zero automated retry or rerouting attempts",
                        "Continuous passive telemetry monitoring for 60 minutes",
                        "Stop when baseline failure rate returns below anomaly threshold",
                        "Escalate to active diagnosis if failure rate escalates above 15%"
                )
        );
    }

    public static StoppingConditionsDto humanReviewRequired() {
        return new StoppingConditionsDto(
                0,
                0,
                0,
                false,
                false,
                0.0,
                true,
                List.of(
                        "Automated recovery strictly locked pending human operator review",
                        "No automated payment mutations allowed",
                        "Requires explicit approval and review by Payment Operations Commander"
                )
        );
    }

    public static StoppingConditionsDto noAction() {
        return new StoppingConditionsDto(
                0,
                0,
                0,
                false,
                true,
                0.0,
                false,
                List.of(
                        "No intervention required",
                        "Telemetry within expected background variance"
                )
        );
    }
}
