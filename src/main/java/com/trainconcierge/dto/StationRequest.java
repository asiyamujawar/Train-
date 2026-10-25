package com.trainconcierge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a station. {@code active} is optional: on create it
 * defaults to {@code true}, on update a null value keeps the current flag.
 */
public record StationRequest(

        @NotBlank(message = "stationCode must not be blank")
        @Size(max = 10, message = "stationCode must be at most 10 characters")
        @Pattern(regexp = "^[A-Za-z0-9]+$", message = "stationCode must contain only letters and digits")
        String stationCode,

        @NotBlank(message = "name must not be blank")
        @Size(max = 120, message = "name must be at most 120 characters")
        String name,

        @NotBlank(message = "city must not be blank")
        @Size(max = 80, message = "city must be at most 80 characters")
        String city,

        @Size(max = 80, message = "state must be at most 80 characters")
        String state,

        Boolean active
) {
}
