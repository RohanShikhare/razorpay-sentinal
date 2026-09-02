package com.sentinelpulse.config;

import com.sentinelpulse.financial.RecoverabilityLevel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The numeric recovery rate applied to each {@link RecoverabilityLevel},
 * bound from {@code sentinel.financial.recoverability-rates.*} in
 * application.yml. These are illustrative business assumptions about what
 * share of a given failure category's value tends to be recoverable - not
 * empirically derived - which is exactly why they're configuration rather
 * than something buried in {@code FinancialImpactService}.
 *
 * Defaults (documented further in the README):
 * <ul>
 *   <li>HIGH_RECOVERABILITY: 0.70</li>
 *   <li>MEDIUM_RECOVERABILITY: 0.35</li>
 *   <li>LOW_RECOVERABILITY: 0.10</li>
 *   <li>UNKNOWN: 0.25</li>
 * </ul>
 */
@Component
@ConfigurationProperties(prefix = "sentinel.financial")
public class RecoverabilityProperties {

    private Map<String, Double> recoverabilityRates = new LinkedHashMap<>(Map.of(
            RecoverabilityLevel.HIGH_RECOVERABILITY.name(), 0.70,
            RecoverabilityLevel.MEDIUM_RECOVERABILITY.name(), 0.35,
            RecoverabilityLevel.LOW_RECOVERABILITY.name(), 0.10,
            RecoverabilityLevel.UNKNOWN.name(), 0.25
    ));

    public Map<String, Double> getRecoverabilityRates() {
        return recoverabilityRates;
    }

    public void setRecoverabilityRates(Map<String, Double> recoverabilityRates) {
        this.recoverabilityRates = recoverabilityRates;
    }

    /**
     * The configured rate (0.0-1.0) for a given level, defaulting to 0 if
     * somehow unconfigured rather than throwing.
     */
    public double rateFor(RecoverabilityLevel level) {
        return recoverabilityRates.getOrDefault(level.name(), 0.0);
    }
}
