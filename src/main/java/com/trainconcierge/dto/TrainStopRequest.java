package com.trainconcierge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalTime;

/**
 * Create/update payload for a train stop (a timetable entry).
 *
 * <p>At least one of {@code arrivalTime} / {@code departureTime} is required;
 * that cross-field rule is enforced in {@code TrainStopService} because a
 * station-level annotation cannot express it.</p>
 */
public record TrainStopRequest(

        @NotNull(message = "stationId must not be null")
        Long stationId,

        @NotNull(message = "stopSequence must not be null")
        @Positive(message = "stopSequence must be a positive integer")
        Integer stopSequence,

        LocalTime arrivalTime,

        LocalTime departureTime,

        @PositiveOrZero(message = "dayOffset must be zero or greater")
        Integer dayOffset,

        @PositiveOrZero(message = "distanceFromSourceKm must be zero or greater")
        Integer distanceFromSourceKm
) {
}
