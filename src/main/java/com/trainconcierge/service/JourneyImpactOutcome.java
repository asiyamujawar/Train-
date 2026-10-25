package com.trainconcierge.service;

/**
 * Counters describing what reconciling one journey did to its impact records.
 *
 * @param created   new impact rows inserted
 * @param updated   active impacts whose severity, delay or explanation changed
 * @param resolved  active impacts closed because their disruption no longer applies
 * @param unchanged active impacts that were confirmed but needed no meaningful change
 */
public record JourneyImpactOutcome(int created, int updated, int resolved, int unchanged) {

    public static JourneyImpactOutcome empty() {
        return new JourneyImpactOutcome(0, 0, 0, 0);
    }
}
