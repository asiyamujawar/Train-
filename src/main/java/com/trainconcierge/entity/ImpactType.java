package com.trainconcierge.entity;

/**
 * The kind of impact a disruption has on one journey.
 *
 * <p>Deterministic rules (see {@code JourneyImpactService}):</p>
 * <ul>
 *   <li>{@link #CANCELLATION} — the leg's train is cancelled for the leg's
 *       service date.</li>
 *   <li>{@link #MISSED_CONNECTION} — the leg's train is significantly delayed
 *       and the expected arrival is later than the next leg's scheduled
 *       departure (only raised when the scheduled times are known).</li>
 *   <li>{@link #DELAY} — the leg's train is significantly delayed but the
 *       connection is not threatened, or there is no next leg.</li>
 *   <li>{@link #ITINERARY_DISRUPTED} — journey-level impact raised when any leg
 *       is cancelled or a connection is missed, i.e. the itinerary can no
 *       longer be completed as planned.</li>
 * </ul>
 */
public enum ImpactType {
    DELAY,
    CANCELLATION,
    MISSED_CONNECTION,
    ITINERARY_DISRUPTED
}
