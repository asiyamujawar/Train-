package com.trainconcierge.exception;

/**
 * Thrown when a requested resource does not exist. Part of the Phase 0
 * exception-handling foundation; used by business modules in later phases.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
