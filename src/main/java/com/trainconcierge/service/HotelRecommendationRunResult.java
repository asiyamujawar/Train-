package com.trainconcierge.service;

import com.trainconcierge.entity.HotelDecisionOutcome;

/**
 * Counters produced by persisting one hotel-evaluation run.
 *
 * @param outcome             decision reached by the run
 * @param relevantStationId   where the passenger is stranded (nullable)
 * @param created             new persisted recommendations
 * @param updated             existing recommendations whose details changed
 * @param expired             previously AVAILABLE rows that are no longer valid
 * @param unchanged           existing recommendations that were already correct
 * @param returned            number of AVAILABLE recommendations after the run
 * @param reason              explanation, especially when nothing was returned
 */
public record HotelRecommendationRunResult(
        HotelDecisionOutcome outcome,
        Long relevantStationId,
        int created,
        int updated,
        int expired,
        int unchanged,
        int returned,
        String reason
) {
}
