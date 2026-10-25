package com.trainconcierge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Phase 4 and Phase 5 configuration, bound from the {@code trainconcierge.*}
 * namespace.
 *
 * <p>Every property can also be supplied through an environment variable using
 * the standard relaxed-binding rules, for example:
 * {@code TRAINCONCIERGE_MONITORING_FIXED_DELAY_MS},
 * {@code TRAINCONCIERGE_MONITORING_ENABLED},
 * {@code TRAINCONCIERGE_DISRUPTION_DELAY_THRESHOLD_MINUTES},
 * {@code TRAINCONCIERGE_DISRUPTION_LOOKBACK_DAYS},
 * {@code TRAINCONCIERGE_DISRUPTION_FORWARD_DAYS},
 * {@code TRAINCONCIERGE_IMPACT_LOOKBACK_DAYS},
 * {@code TRAINCONCIERGE_IMPACT_FORWARD_DAYS},
 * {@code TRAINCONCIERGE_RECOMMENDATION_MIN_TRANSFER_BUFFER_MINUTES},
 * {@code TRAINCONCIERGE_RECOMMENDATION_MAX_RESULTS_PER_IMPACT},
 * {@code TRAINCONCIERGE_RECOMMENDATION_MAX_IMPACTS_PER_RUN}.</p>
 */
@ConfigurationProperties(prefix = "trainconcierge")
public class TrainConciergeProperties {

    private final Monitoring monitoring = new Monitoring();
    private final Disruption disruption = new Disruption();
    private final Impact impact = new Impact();
    private final Recommendation recommendation = new Recommendation();

    public Monitoring getMonitoring() {
        return monitoring;
    }

    public Disruption getDisruption() {
        return disruption;
    }

    public Impact getImpact() {
        return impact;
    }

    public Recommendation getRecommendation() {
        return recommendation;
    }

    public static class Monitoring {

        /** Fixed delay between automatic monitoring runs, in milliseconds. */
        private long fixedDelayMs = 60_000L;

        /** Set to false to disable the scheduled monitor (tests, local debugging). */
        private boolean enabled = true;

        public long getFixedDelayMs() {
            return fixedDelayMs;
        }

        public void setFixedDelayMs(long fixedDelayMs) {
            this.fixedDelayMs = fixedDelayMs;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Disruption {

        /** A delay at or above this many minutes is a SIGNIFICANT_DELAY. */
        private int delayThresholdMinutes = 30;

        /** How many days before today are still monitored. */
        private int lookbackDays = 1;

        /** How many days after today are still monitored. */
        private int forwardDays = 7;

        public int getDelayThresholdMinutes() {
            return delayThresholdMinutes;
        }

        public void setDelayThresholdMinutes(int delayThresholdMinutes) {
            this.delayThresholdMinutes = delayThresholdMinutes;
        }

        public int getLookbackDays() {
            return lookbackDays;
        }

        public void setLookbackDays(int lookbackDays) {
            this.lookbackDays = lookbackDays;
        }

        public int getForwardDays() {
            return forwardDays;
        }

        public void setForwardDays(int forwardDays) {
            this.forwardDays = forwardDays;
        }
    }

    public static class Impact {

        /** How many days before today are still analysed. */
        private int lookbackDays = 1;

        /** How many days after today are still analysed. */
        private int forwardDays = 7;

        public int getLookbackDays() {
            return lookbackDays;
        }

        public void setLookbackDays(int lookbackDays) {
            this.lookbackDays = lookbackDays;
        }

        public int getForwardDays() {
            return forwardDays;
        }

        public void setForwardDays(int forwardDays) {
            this.forwardDays = forwardDays;
        }
    }

    /**
     * Phase 6 alternative-train recommendation settings.
     */
    public static class Recommendation {

        /**
         * Minimum minutes that must remain between a replacement's arrival and
         * the next leg's scheduled departure for the connection to be treated as
         * reachable.
         */
        private int minTransferBufferMinutes = 15;

        /** Maximum number of recommendations kept (and returned) per impact. */
        private int maxResultsPerImpact = 10;

        /** Maximum number of impacts reconciled by one bounded batch run. */
        private int maxImpactsPerRun = 50;

        public int getMinTransferBufferMinutes() {
            return minTransferBufferMinutes;
        }

        public void setMinTransferBufferMinutes(int minTransferBufferMinutes) {
            this.minTransferBufferMinutes = minTransferBufferMinutes;
        }

        public int getMaxResultsPerImpact() {
            return maxResultsPerImpact;
        }

        public void setMaxResultsPerImpact(int maxResultsPerImpact) {
            this.maxResultsPerImpact = maxResultsPerImpact;
        }

        public int getMaxImpactsPerRun() {
            return maxImpactsPerRun;
        }

        public void setMaxImpactsPerRun(int maxImpactsPerRun) {
            this.maxImpactsPerRun = maxImpactsPerRun;
        }
    }
}
