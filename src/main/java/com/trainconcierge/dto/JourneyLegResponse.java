package com.trainconcierge.dto;

import com.trainconcierge.entity.JourneyLeg;
import java.time.LocalTime;

public record JourneyLegResponse(
        Long id,
        Long trainId,
        String trainNumber,
        String trainName,
        StationSummaryResponse sourceStation,
        StationSummaryResponse destinationStation,
        Integer legSequence,
        LocalTime departureTime,
        LocalTime arrivalTime
) {
    public static JourneyLegResponse from(JourneyLeg leg) {
        return new JourneyLegResponse(
                leg.getId(),
                leg.getTrain().getId(),
                leg.getTrain().getTrainNumber(),
                leg.getTrain().getName(),
                StationSummaryResponse.from(leg.getSourceStation()),
                StationSummaryResponse.from(leg.getDestinationStation()),
                leg.getLegSequence(),
                leg.getDepartureTime(),
                leg.getArrivalTime());
    }
}
