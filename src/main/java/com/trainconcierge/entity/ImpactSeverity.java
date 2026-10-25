package com.trainconcierge.entity;

/**
 * How badly a journey is affected. The value is always computed from the
 * disruption, never random.
 *
 * <p>Deterministic rules (see {@code JourneyImpactService}):</p>
 * <ul>
 *   <li>{@code CANCELLATION} and {@code ITINERARY_DISRUPTED} → {@link #CRITICAL}</li>
 *   <li>{@code MISSED_CONNECTION} by how far the delay exceeds the transfer
 *       buffer: {@code >= 60} min → {@link #CRITICAL}, {@code >= 30} min →
 *       {@link #HIGH}, otherwise {@link #MEDIUM}</li>
 *   <li>{@code DELAY} by the delay itself: {@code >= 120} min → {@link #HIGH},
 *       {@code >= 60} min → {@link #MEDIUM}, otherwise {@link #LOW}</li>
 * </ul>
 */
public enum ImpactSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
