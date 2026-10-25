package com.trainconcierge.dto;

import com.trainconcierge.entity.TrainType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a train. {@code active} behaves like its station
 * counterpart: null means "true" on create and "keep current value" on update.
 */
public record TrainRequest(

        @NotBlank(message = "trainNumber must not be blank")
        @Size(max = 10, message = "trainNumber must be at most 10 characters")
        @Pattern(regexp = "^[0-9]+$", message = "trainNumber must contain only digits")
        String trainNumber,

        @NotBlank(message = "name must not be blank")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        TrainType trainType,

        @NotNull(message = "sourceStationId must not be null")
        Long sourceStationId,

        @NotNull(message = "destinationStationId must not be null")
        Long destinationStationId,

        Boolean active
) {
}
