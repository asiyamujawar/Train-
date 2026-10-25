package com.trainconcierge.service;

/**
 * Counters describing the outcome of one journey impact-analysis cycle.
 *
 * @param journeysInspected  journeys analysed (journeys in the date window that
 *                           have an ongoing disruption, plus journeys that still
 *                           carry an active impact)
 * @param eventsInspected    ongoing disruption events considered
 * @param impactsCreated     new impact rows inserted
 * @param impactsUpdated     active impacts whose details changed
 * @param impactsResolved    active impacts closed because the disruption ended
 * @param impactsUnchanged   active impacts confirmed without a meaningful change
 * @param failures           journeys whose analysis failed (the rest still commit)
 */
public record ImpactAnalysisResult(
        int journeysInspected,
        int eventsInspected,
        int impactsCreated,
        int impactsUpdated,
        int impactsResolved,
        int impactsUnchanged,
        int failures
) {
    public static ImpactAnalysisResult empty() {
        return new ImpactAnalysisResult(0, 0, 0, 0, 0, 0, 0);
    }
}
