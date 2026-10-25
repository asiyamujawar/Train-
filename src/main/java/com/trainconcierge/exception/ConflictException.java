package com.trainconcierge.exception;

/**
 * Thrown when a request would create or produce conflicting data (for example a
 * duplicate station code or train number). Mapped to HTTP {@code 409 Conflict}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
