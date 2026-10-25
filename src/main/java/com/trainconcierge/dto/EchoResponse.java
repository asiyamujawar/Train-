package com.trainconcierge.dto;

import java.time.OffsetDateTime;

/**
 * Response of the validation demo endpoint.
 *
 * @param message       the validated message that was echoed back
 * @param messageLength number of characters in the echoed message
 * @param processedAt   server timestamp of the response
 */
public record EchoResponse(
        String message,
        int messageLength,
        OffsetDateTime processedAt
) {
}
