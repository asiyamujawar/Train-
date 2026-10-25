package com.trainconcierge.dto;

import com.trainconcierge.service.ImpactAnalysisResult;

/** Response body for one journey impact-analysis cycle. */
public record ImpactAnalysisRunResponse(
        int journeysInspected,
        int eventsInspected,
        int impactsCreated,
        int impactsUpdated,
        int impactsResolved,
        int impactsUnchanged,
        int failures
) {
    public static ImpactAnalysisRunResponse from(ImpactAnalysisResult result) {
        return new ImpactAnalysisRunResponse(
                result.journeysInspected(),
                result.eventsInspected(),
                result.impactsCreated(),
                result.impactsUpdated(),
                result.impactsResolved(),
                result.impactsUnchanged(),
                result.failures());
    }
}
