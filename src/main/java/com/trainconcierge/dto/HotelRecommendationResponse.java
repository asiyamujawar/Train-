package com.trainconcierge.dto;

import com.trainconcierge.entity.HotelDecisionOutcome;
import com.trainconcierge.service.HotelRecommendationResult;
import java.util.List;

/**
 * The full evaluation of whether a disrupted journey needs an overnight stay,
 * together with the ranked hotel suggestions when it does.
 *
 * @param journeyId          the evaluated journey
 * @param journeyReference   the journey's human-readable reference
 * @param decisionOutcome    {@code OVERNIGHT_STAY_LIKELY},
 *                           {@code SAME_DAY_TRAVEL_FEASIBLE} or
 *                           {@code INSUFFICIENT_TRAVEL_DATA}
 * @param overnightStayLikely convenience flag for {@code OVERNIGHT_STAY_LIKELY}
 * @param relevantStation    where the passenger is stranded (nullable)
 * @param decisionReason     why the outcome was reached
 * @param recommendations    ranked hotels (empty unless an overnight is likely)
 */
public record HotelRecommendationResponse(
        Long journeyId,
        String journeyReference,
        HotelDecisionOutcome decisionOutcome,
        boolean overnightStayLikely,
        StationSummaryResponse relevantStation,
        String decisionReason,
        List<HotelRecommendationItem> recommendations
) {

    public static HotelRecommendationResponse from(HotelRecommendationResult result) {
        return new HotelRecommendationResponse(
                result.journey().getId(),
                result.journey().getJourneyReference(),
                result.outcome(),
                result.outcome() == HotelDecisionOutcome.OVERNIGHT_STAY_LIKELY,
                result.relevantStation() == null ? null : StationSummaryResponse.from(result.relevantStation()),
                result.reason(),
                result.recommendations());
    }
}
