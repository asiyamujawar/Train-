package com.trainconcierge.dto;

import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.JourneyStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record JourneyResponse(
        Long id,
        String journeyReference,
        PassengerSummaryResponse passenger,
        LocalDate journeyDate,
        JourneyStatus status,
        List<JourneyLegResponse> legs,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static JourneyResponse from(Journey journey) {
        return new JourneyResponse(
                journey.getId(),
                journey.getJourneyReference(),
                PassengerSummaryResponse.from(journey.getPassenger()),
                journey.getJourneyDate(),
                journey.getStatus(),
                journey.getLegs().stream().map(JourneyLegResponse::from).toList(),
                journey.getCreatedAt(),
                journey.getUpdatedAt());
    }
}
