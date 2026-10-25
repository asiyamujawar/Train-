package com.trainconcierge.entity;

/**
 * How trustworthy a hotel's availability information is.
 *
 * <p>TrainConcierge has <strong>no</strong> booking or live-availability
 * integration, so there is deliberately no {@code CONFIRMED} value. A hotel can
 * only be either seeded illustrative demo data or a record whose availability
 * was never verified.</p>
 */
public enum HotelAvailabilityStatus {
    /** Seeded, clearly fictional demonstration data (never a real business). */
    DEMO_DATA,
    /** A user/moderator-entered hotel whose availability is not verified. */
    AVAILABILITY_UNKNOWN
}
