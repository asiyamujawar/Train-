package com.trainconcierge.entity;

/**
 * Who caused a status transition. Kept as a controlled value so that history
 * rows cannot carry arbitrary, client-supplied origins.
 */
public enum StatusSource {
    /** A demonstration status changed through the simulation APIs. */
    SIMULATION,
    /** A status changed by an administrative operation. */
    ADMIN,
    /** A status changed by the system itself. */
    SYSTEM
}
