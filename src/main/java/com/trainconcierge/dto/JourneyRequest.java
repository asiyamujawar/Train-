package com.trainconcierge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

public record JourneyRequest(
        @NotNull(message = "passengerId must not be null")
        Long passengerId,

        @NotNull(message = "journeyDate must not be null")
        LocalDate journeyDate,

        @NotEmpty(message = "journey must have at least one leg")
        @Valid
        List<JourneyLegRequest> legs
) {
}
