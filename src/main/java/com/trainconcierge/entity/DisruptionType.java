package com.trainconcierge.entity;

/** A disruption detected from a train's simulated running status. */
public enum DisruptionType {
    /** A delay at or above the configured threshold. */
    SIGNIFICANT_DELAY,
    /** The train was cancelled. */
    CANCELLATION
}
