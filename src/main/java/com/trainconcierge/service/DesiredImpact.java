package com.trainconcierge.service;

import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.ImpactSeverity;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.JourneyLeg;

/**
 * One impact that the current state of the data says should exist.
 *
 * <p>{@code activeKey} is the derived identity used for idempotency, and
 * {@code leg} is {@code null} for journey-level impacts.</p>
 */
record DesiredImpact(
        String activeKey,
        JourneyLeg leg,
        DisruptionEvent event,
        ImpactType impactType,
        ImpactSeverity severity,
        Integer delayMinutes,
        String explanation
) {
}
