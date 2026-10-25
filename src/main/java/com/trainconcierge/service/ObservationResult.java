package com.trainconcierge.service;

/**
 * How observing one train status affected the disruption events.
 *
 * <p>Counters rather than a single enum because one observation can both resolve
 * an incompatible event and create a new one (for example delay -&gt; cancellation).</p>
 */
public record ObservationResult(int created, int updated, int resolved) {

    public static ObservationResult none() {
        return new ObservationResult(0, 0, 0);
    }

    public static ObservationResult ofUpdated() {
        return new ObservationResult(0, 1, 0);
    }
}
