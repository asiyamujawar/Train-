package com.trainconcierge.dto;

import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.ImpactSeverity;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.JourneyLeg;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A journey impact record. Journey, leg and disruption details are flattened so
 * the JPA entity is never serialised directly.
 */
public record JourneyImpactResponse(
        Long id,
        Long journeyId,
        String journeyReference,
        Long journeyLegId,
        Integer legSequence,
        String legTrainNumber,
        Long disruptionEventId,
        DisruptionType disruptionType,
        LocalDate serviceDate,
        ImpactType impactType,
        ImpactSeverity severity,
        Integer delayMinutes,
        String explanation,
        OffsetDateTime detectedAt,
        OffsetDateTime lastObservedAt,
        ImpactState impactState,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** Requires the journey, leg and disruption event to be loadable. */
    public static JourneyImpactResponse from(JourneyImpact impact) {
        JourneyLeg leg = impact.getJourneyLeg();
        return new JourneyImpactResponse(
                impact.getId(),
                impact.getJourney().getId(),
                impact.getJourney().getJourneyReference(),
                leg == null ? null : leg.getId(),
                leg == null ? null : leg.getLegSequence(),
                leg == null ? null : leg.getTrain().getTrainNumber(),
                impact.getDisruptionEvent().getId(),
                impact.getDisruptionEvent().getDisruptionType(),
                impact.getDisruptionEvent().getServiceDate(),
                impact.getImpactType(),
                impact.getSeverity(),
                impact.getDelayMinutes(),
                impact.getExplanation(),
                impact.getDetectedAt(),
                impact.getLastObservedAt(),
                impact.getImpactState(),
                impact.getResolvedAt(),
                impact.getCreatedAt(),
                impact.getUpdatedAt());
    }
}
