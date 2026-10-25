package com.trainconcierge.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PassengerRequest(
        @NotBlank(message = "fullName must not be blank")
        @Size(max = 150, message = "fullName must be at most 150 characters")
        String fullName,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 150, message = "email must be at most 150 characters")
        String email,

        @Size(max = 20, message = "phoneNumber must be at most 20 characters")
        String phoneNumber,

        Boolean active
) {
}
