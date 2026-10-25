package com.trainconcierge.entity;

/**
 * Lifecycle of an alternative-train recommendation.
 *
 * <p>The Phase 6 engine produces only two of these values:</p>
 * <ul>
 *   <li>{@link #AVAILABLE} — the candidate is currently feasible for the
 *       affected leg; it holds the unique {@code activeKey}.</li>
 *   <li>{@link #EXPIRED} — the candidate was available but is no longer feasible
 *       (disruption resolved, a candidate got cancelled, its connection is no
 *       longer reachable, ...). The key is released so the row is kept as
 *       history.</li>
 * </ul>
 *
 * <p>{@link #SELECTED} and {@link #UNAVAILABLE} are part of the documented model
 * but are deliberately not produced: Phase 6 has no passenger acceptance
 * workflow and never marks a candidate unusable on speculative grounds. They
 * exist so a later phase can adopt them without a schema change.</p>
 */
public enum RecommendationStatus {
    AVAILABLE,
    SELECTED,
    EXPIRED,
    UNAVAILABLE
}
