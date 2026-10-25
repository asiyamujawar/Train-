package com.trainconcierge.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Request body shared by the operation-specific status endpoints
 * ({@code on-time}, {@code delay}, {@code cancel}, {@code restore}).
 *
 * <p>{@code delayMinutes} is required and must be strictly positive for the
 * {@code delay} endpoint; it is ignored by the other operation endpoints.</p>
 */
public record TrainStatusActionRequest(
        @NotNull(message = "serviceDate must not be null")
        LocalDate serviceDate,

        @PositiveOrZero(message = "delayMinutes must be zero or positive")
        Integer delayMinutes,

        Long currentStationId,

        @Size(max = 255, message = "reason must be at most 255 characters")
        String reason
) {
}
