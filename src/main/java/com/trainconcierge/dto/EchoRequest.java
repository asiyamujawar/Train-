package com.trainconcierge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body used to prove that Jakarta Bean Validation is wired up.
 *
 * @param message text to echo back; must not be blank and at most 200 characters
 */
public record EchoRequest(

        @NotBlank(message = "message must not be blank")
        @Size(max = 200, message = "message must not exceed 200 characters")
        String message
) {
}
