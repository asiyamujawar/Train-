package com.trainconcierge.exception;

/**
 * Thrown when a request is syntactically valid but breaks a business rule
 * (for example a train whose source and destination stations are the same).
 * Mapped to HTTP {@code 400 Bad Request}.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
