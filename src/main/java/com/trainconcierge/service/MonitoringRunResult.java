package com.trainconcierge.service;

/**
 * Counters describing the outcome of one disruption-monitoring cycle.
 *
 * @param statusesInspected how many train statuses inside the monitoring window were examined
 * @param eventsCreated     new disruption events inserted
 * @param eventsUpdated     ongoing events whose observation was refreshed or whose delay/reason changed
 * @param eventsResolved    ongoing events closed because the disruption ended or changed type
 * @param failures          per-status observations that failed (the rest still commit)
 */
public record MonitoringRunResult(
        int statusesInspected,
        int eventsCreated,
        int eventsUpdated,
        int eventsResolved,
        int failures
) {
    public static MonitoringRunResult empty() {
        return new MonitoringRunResult(0, 0, 0, 0, 0);
    }

    public MonitoringRunResult plus(MonitoringRunResult other) {
        return new MonitoringRunResult(
                statusesInspected + other.statusesInspected,
                eventsCreated + other.eventsCreated,
                eventsUpdated + other.eventsUpdated,
                eventsResolved + other.eventsResolved,
                failures + other.failures);
    }
}
