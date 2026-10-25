package com.trainconcierge.dto;

import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

/** Current simulated running status of a train for one service date. */
public record TrainStatusResponse(
        Long id,
        Long trainId,
        String trainNumber,
        String trainName,
        LocalDate serviceDate,
        TrainRunningStatus status,
        int delayMinutes,
        StationSummaryResponse currentStation,
        LocalTime expectedDepartureTime,
        LocalTime expectedArrivalTime,
        String reason,
        OffsetDateTime lastUpdatedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    /** Requires the train (and current station, if any) to be loaded already. */
    public static TrainStatusResponse from(TrainStatus status) {
        return new TrainStatusResponse(
                status.getId(),
                status.getTrain().getId(),
                status.getTrain().getTrainNumber(),
                status.getTrain().getName(),
                status.getServiceDate(),
                status.getStatus(),
                status.getDelayMinutes(),
                status.getCurrentStation() == null ? null : StationSummaryResponse.from(status.getCurrentStation()),
                status.getExpectedDepartureTime(),
                status.getExpectedArrivalTime(),
                status.getReason(),
                status.getLastUpdatedAt(),
                status.getCreatedAt(),
                status.getUpdatedAt());
    }
}
