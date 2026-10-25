package com.trainconcierge.dto;

import com.trainconcierge.entity.HotelDecisionOutcome;
import com.trainconcierge.service.HotelRecommendationRunResult;

/**
 * Summary returned by {@code POST /api/v1/journeys/{journeyId}/hotel-recommendations/run}.
 *
 * @param journeyId                the evaluated journey
 * @param decisionOutcome          the outcome of this run
 * @param overnightStayLikely      convenience flag for {@code OVERNIGHT_STAY_LIKELY}
 * @param relevantStationId        where the passenger is stranded (nullable)
 * @param recommendationsCreated   new persisted recommendations
 * @param recommendationsUpdated   existing recommendations whose details changed
 * @param recommendationsExpired   previously AVAILABLE rows that are no longer valid
 * @param recommendationsUnchanged existing recommendations that were already correct
 * @param recommendationsReturned  number of AVAILABLE recommendations after the run
 * @param reason                   explanation, especially when nothing was returned
 */
public record HotelRecommendationRunResponse(
        Long journeyId,
        HotelDecisionOutcome decisionOutcome,
        boolean overnightStayLikely,
        Long relevantStationId,
        int recommendationsCreated,
        int recommendationsUpdated,
        int recommendationsExpired,
        int recommendationsUnchanged,
        int recommendationsReturned,
        String reason
) {

    public static HotelRecommendationRunResponse from(Long journeyId, HotelRecommendationRunResult result) {
        return new HotelRecommendationRunResponse(
                journeyId,
                result.outcome(),
                result.outcome() == HotelDecisionOutcome.OVERNIGHT_STAY_LIKELY,
                result.relevantStationId(),
                result.created(),
                result.updated(),
                result.expired(),
                result.unchanged(),
                result.returned(),
                result.reason());
    }
}
