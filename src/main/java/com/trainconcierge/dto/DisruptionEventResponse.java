package com.trainconcierge.dto;

import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.EventState;
import com.trainconcierge.entity.TrainRunningStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** A detected disruption event. */
public record DisruptionEventResponse(
        Long id,
        Long trainId,
        String trainNumber,
        String trainName,
        LocalDate serviceDate,
        DisruptionType disruptionType,
        TrainRunningStatus observedTrainStatus,
        int delayMinutes,
        String reason,
        OffsetDateTime detectedAt,
        OffsetDateTime lastObservedAt,
        EventState eventState,
        OffsetDateTime resolvedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** Requires the train to be loaded already. */
    public static DisruptionEventResponse from(DisruptionEvent event) {
        return new DisruptionEventResponse(
                event.getId(),
                event.getTrain().getId(),
                event.getTrain().getTrainNumber(),
                event.getTrain().getName(),
                event.getServiceDate(),
                event.getDisruptionType(),
                event.getObservedTrainStatus(),
                event.getDelayMinutes(),
                event.getReason(),
                event.getDetectedAt(),
                event.getLastObservedAt(),
                event.getEventState(),
                event.getResolvedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt());
    }
}
