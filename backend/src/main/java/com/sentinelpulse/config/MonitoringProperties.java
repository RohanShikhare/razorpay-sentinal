package com.sentinelpulse.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * All tunable numbers the Monitoring & Incident Detection Engine uses,
 * bound from {@code sentinel.monitoring.*} in application.yml. Kept in one
 * place so severity/window/sample-size decisions aren't scattered as magic
 * numbers through {@code IncidentDetectionService}.
 */
@Component
@ConfigurationProperties(prefix = "sentinel.monitoring")
public class MonitoringProperties {

    /**
     * Default size (minutes) of the "current" monitoring window when the
     * caller doesn't specify one.
     */
    private int defaultWindowMinutes = 60;

    /**
     * If the current window contains fewer than this many transactions, the
     * window is doubled (up to {@link #maxWindowMinutes}) until it does, so
     * a narrow window over a sparse synthetic dataset doesn't produce
     * statistically meaningless swings. The effective window actually used
     * is always reported in the API response.
     */
    private int minimumSampleSize = 100;

    /**
     * A bank/payment-method/merchant segment needs at least this many
     * transactions (combined across baseline + current period) before it's
     * considered for segment-level detection - otherwise a handful of
     * transactions could swing a "failure rate" to a meaningless extreme.
     */
    private int minimumSegmentSampleSize = 15;

    /**
     * Ceiling for the adaptive window-widening in {@link #minimumSampleSize}.
     * Defaults to the same span as the payment generator's default
     * generation window (48h), since widening further wouldn't reach more data.
     */
    private int maxWindowMinutes = 48 * 60;

    private final Thresholds thresholds = new Thresholds();

    public int getDefaultWindowMinutes() {
        return defaultWindowMinutes;
    }

    public void setDefaultWindowMinutes(int defaultWindowMinutes) {
        this.defaultWindowMinutes = defaultWindowMinutes;
    }

    public int getMinimumSampleSize() {
        return minimumSampleSize;
    }

    public void setMinimumSampleSize(int minimumSampleSize) {
        this.minimumSampleSize = minimumSampleSize;
    }

    public int getMinimumSegmentSampleSize() {
        return minimumSegmentSampleSize;
    }

    public void setMinimumSegmentSampleSize(int minimumSegmentSampleSize) {
        this.minimumSegmentSampleSize = minimumSegmentSampleSize;
    }

    public int getMaxWindowMinutes() {
        return maxWindowMinutes;
    }

    public void setMaxWindowMinutes(int maxWindowMinutes) {
        this.maxWindowMinutes = maxWindowMinutes;
    }

    public Thresholds getThresholds() {
        return thresholds;
    }

    /**
     * Percentage-point (not relative %) increase in failure rate, over the
     * relevant baseline, required to reach each level. Evaluated against
     * whichever is larger: the global increase, or the most-deviated
     * qualifying segment's increase.
     */
    public static class Thresholds {

        /** Below this, the deviation is treated as normal noise - no incident. */
        private double minimumIncreasePoints = 5.0;

        private double lowIncreasePoints = 5.0;
        private double mediumIncreasePoints = 12.0;
        private double highIncreasePoints = 20.0;
        private double criticalIncreasePoints = 30.0;

        public double getMinimumIncreasePoints() {
            return minimumIncreasePoints;
        }

        public void setMinimumIncreasePoints(double minimumIncreasePoints) {
            this.minimumIncreasePoints = minimumIncreasePoints;
        }

        public double getLowIncreasePoints() {
            return lowIncreasePoints;
        }

        public void setLowIncreasePoints(double lowIncreasePoints) {
            this.lowIncreasePoints = lowIncreasePoints;
        }

        public double getMediumIncreasePoints() {
            return mediumIncreasePoints;
        }

        public void setMediumIncreasePoints(double mediumIncreasePoints) {
            this.mediumIncreasePoints = mediumIncreasePoints;
        }

        public double getHighIncreasePoints() {
            return highIncreasePoints;
        }

        public void setHighIncreasePoints(double highIncreasePoints) {
            this.highIncreasePoints = highIncreasePoints;
        }

        public double getCriticalIncreasePoints() {
            return criticalIncreasePoints;
        }

        public void setCriticalIncreasePoints(double criticalIncreasePoints) {
            this.criticalIncreasePoints = criticalIncreasePoints;
        }
    }
}
