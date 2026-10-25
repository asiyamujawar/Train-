package com.trainconcierge.dto;

import com.trainconcierge.entity.TrainRunningStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Request body for the full create-or-update status endpoint
 * ({@code PUT /api/v1/trains/{trainId}/status}).
 *
 * <p>Cross-field rules (on-time = 0 delay, delayed &gt; 0 delay) are enforced in
 * the service, together with the train/station existence checks.</p>
 */
public record TrainStatusRequest(
        @NotNull(message = "serviceDate must not be null")
        LocalDate serviceDate,

        @NotNull(message = "status must not be null")
        TrainRunningStatus status,

        @PositiveOrZero(message = "delayMinutes must be zero or positive")
        Integer delayMinutes,

        Long currentStationId,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason,

        LocalTime expectedDepartureTime,

        LocalTime expectedArrivalTime
) {
}
