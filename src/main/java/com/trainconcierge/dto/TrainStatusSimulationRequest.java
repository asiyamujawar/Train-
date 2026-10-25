package com.trainconcierge.dto;

import com.trainconcierge.entity.TrainRunningStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body for the convenience simulation endpoint
 * ({@code POST /api/v1/simulations/train-status}). It carries the train id
 * because the endpoint is not nested under a train, and it delegates to the
 * exact same service logic as the other status endpoints.
 */
public record TrainStatusSimulationRequest(
        @NotNull(message = "trainId must not be null")
        Long trainId,

        @NotNull(message = "serviceDate must not be null")
        LocalDate serviceDate,

        @NotNull(message = "status must not be null")
        TrainRunningStatus status,

        @PositiveOrZero(message = "delayMinutes must be zero or positive")
        Integer delayMinutes,

        Long currentStationId,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason
) {
}
