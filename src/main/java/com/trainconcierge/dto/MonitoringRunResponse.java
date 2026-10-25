package com.trainconcierge.dto;

import com.trainconcierge.service.MonitoringRunResult;

/** Response body for a manual or scheduled monitoring cycle. */
public record MonitoringRunResponse(
        int statusesInspected,
        int eventsCreated,
        int eventsUpdated,
        int eventsResolved,
        int failures
) {
    public static MonitoringRunResponse from(MonitoringRunResult result) {
        return new MonitoringRunResponse(
                result.statusesInspected(),
                result.eventsCreated(),
                result.eventsUpdated(),
                result.eventsResolved(),
                result.failures());
    }
}
