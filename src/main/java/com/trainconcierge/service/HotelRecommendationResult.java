package com.trainconcierge.service;

import com.trainconcierge.dto.HotelRecommendationItem;
import com.trainconcierge.entity.HotelDecisionOutcome;
import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.Station;
import java.util.List;

/**
 * Internal result of one hotel evaluation: the decision, where the passenger is
 * stranded and the ranked hotel suggestions (empty unless an overnight is
 * likely). Converted to a DTO by the controller.
 */
public record HotelRecommendationResult(
        Journey journey,
        HotelDecisionOutcome outcome,
        Station relevantStation,
        Long forcingImpactId,
        String reason,
        List<HotelRecommendationItem> recommendations
) {
}
