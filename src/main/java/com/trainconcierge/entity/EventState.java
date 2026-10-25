package com.trainconcierge.entity;

/**
 * Lifecycle of a disruption event.
 *
 * <p>ACTIVE and ACKNOWLEDGED are both "ongoing" states: the disruption is still
 * present and the event still holds its unique {@code activeKey}. RESOLVED is
 * terminal and releases the key so a later recurrence can be recorded.</p>
 */
public enum EventState {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}
