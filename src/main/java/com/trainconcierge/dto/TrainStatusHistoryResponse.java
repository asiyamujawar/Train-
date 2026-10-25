package com.trainconcierge.dto;

import com.trainconcierge.entity.StatusSource;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStatusHistory;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** One immutable status-transition record. */
public record TrainStatusHistoryResponse(
        Long id,
        Long trainId,
        LocalDate serviceDate,
        TrainRunningStatus previousStatus,
        TrainRunningStatus newStatus,
        Integer previousDelayMinutes,
        int newDelayMinutes,
        StationSummaryResponse currentStation,
        String reason,
        StatusSource source,
        OffsetDateTime changedAt
) {
    public static TrainStatusHistoryResponse from(TrainStatusHistory history) {
        return new TrainStatusHistoryResponse(
                history.getId(),
                history.getTrain().getId(),
                history.getServiceDate(),
                history.getPreviousStatus(),
                history.getNewStatus(),
                history.getPreviousDelayMinutes(),
                history.getNewDelayMinutes(),
                history.getCurrentStation() == null ? null : StationSummaryResponse.from(history.getCurrentStation()),
                history.getReason(),
                history.getSource(),
                history.getChangedAt());
    }
}
