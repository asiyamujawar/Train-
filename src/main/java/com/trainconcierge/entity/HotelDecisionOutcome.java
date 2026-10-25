package com.trainconcierge.entity;

/**
 * The deterministic outcome of evaluating whether a disrupted journey is likely
 * to need an overnight hotel stay.
 */
public enum HotelDecisionOutcome {
    /** The disruption leaves no feasible same-day onward travel. */
    OVERNIGHT_STAY_LIKELY,
    /** The disruption does not prevent continuing the journey the same day. */
    SAME_DAY_TRAVEL_FEASIBLE,
    /** The stored data is not sufficient to decide either way. */
    INSUFFICIENT_TRAVEL_DATA
}
