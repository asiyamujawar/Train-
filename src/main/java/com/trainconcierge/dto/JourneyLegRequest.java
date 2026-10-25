package com.trainconcierge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record JourneyLegRequest(
        @NotNull(message = "trainId must not be null")
        Long trainId,

        @NotNull(message = "sourceStationId must not be null")
        Long sourceStationId,

        @NotNull(message = "destinationStationId must not be null")
        Long destinationStationId,

        @NotNull(message = "legSequence must not be null")
        @Positive(message = "legSequence must be positive")
        Integer legSequence
) {
}
