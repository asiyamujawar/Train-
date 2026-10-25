package com.trainconcierge.entity;

/**
 * Lifecycle of a journey impact.
 *
 * <p>ACTIVE is the only ongoing state and the only one that holds the unique
 * {@code activeKey}. RESOLVED is terminal and releases the key so a later
 * recurrence can be recorded.</p>
 */
public enum ImpactState {
    ACTIVE,
    RESOLVED
}
