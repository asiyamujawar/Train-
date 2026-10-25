package com.trainconcierge.dto;

import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.JourneyLeg;
import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRecommendation;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/**
 * An alternative-train recommendation. Journey, leg, impact, disruption and
 * candidate-train details are flattened so the JPA entity is never serialised
 * directly, and a candidate's live status is reported with an explicit
 * {@code liveStatusKnown} flag (an unknown status is never presented as on time).
 */
public record TrainRecommendationResponse(
        Long id,
        Long journeyId,
        String journeyReference,
        Long journeyLegId,
        Integer legSequence,
        Long journeyImpactId,
        ImpactType impactType,
        Long disruptionEventId,
        Long replacementTrainId,
        String replacementTrainNumber,
        String replacementTrainName,
        TrainType replacementTrainType,
        LocalDate serviceDate,
        StationSummaryResponse replacementSourceStation,
        StationSummaryResponse replacementDestinationStation,
        LocalTime replacementDepartureTime,
        Integer replacementDepartureDayOffset,
        LocalTime replacementArrivalTime,
        Integer replacementArrivalDayOffset,
        int transferCount,
        Integer additionalMinutes,
        long score,
        RecommendationStatus recommendationStatus,
        boolean liveStatusKnown,
        TrainRunningStatus liveStatus,
        Integer liveDelayMinutes,
        String explanation,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /**
     * Requires the journey, impact, candidate train and leg to be loadable.
     */
    public static TrainRecommendationResponse from(TrainRecommendation recommendation) {
        JourneyLeg leg = recommendation.getJourneyLeg();
        Train train = recommendation.getTrain();

        return new TrainRecommendationResponse(
                recommendation.getId(),
                recommendation.getJourney().getId(),
                recommendation.getJourney().getJourneyReference(),
                leg == null ? null : leg.getId(),
                leg == null ? null : leg.getLegSequence(),
                recommendation.getJourneyImpact().getId(),
                recommendation.getImpactType(),
                recommendation.getDisruptionEvent() == null
                        ? null : recommendation.getDisruptionEvent().getId(),
                train.getId(),
                train.getTrainNumber(),
                train.getName(),
                train.getTrainType(),
                recommendation.getServiceDate(),
                leg == null ? null : StationSummaryResponse.from(leg.getSourceStation()),
                leg == null ? null : StationSummaryResponse.from(leg.getDestinationStation()),
                recommendation.getDepartureTime(),
                recommendation.getDepartureDayOffset(),
                recommendation.getArrivalTime(),
                recommendation.getArrivalDayOffset(),
                recommendation.getTransferCount(),
                recommendation.getAdditionalMinutes(),
                recommendation.getScore(),
                recommendation.getRecommendationStatus(),
                recommendation.isStatusKnown(),
                recommendation.getCandidateStatus(),
                recommendation.getCandidateDelayMinutes(),
                recommendation.getExplanation(),
                recommendation.getCreatedAt(),
                recommendation.getUpdatedAt());
    }
}
