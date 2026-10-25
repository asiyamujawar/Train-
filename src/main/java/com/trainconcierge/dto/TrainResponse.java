package com.trainconcierge.dto;

import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainType;
import java.time.OffsetDateTime;

/** Full train representation, including both stations as summaries. */
public record TrainResponse(
        Long id,
        String trainNumber,
        String name,
        TrainType trainType,
        boolean active,
        StationSummaryResponse sourceStation,
        StationSummaryResponse destinationStation,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    /** Requires the source and destination stations to be loaded already. */
    public static TrainResponse from(Train train) {
        return new TrainResponse(
                train.getId(),
                train.getTrainNumber(),
                train.getName(),
                train.getTrainType(),
                train.isActive(),
                StationSummaryResponse.from(train.getSourceStation()),
                StationSummaryResponse.from(train.getDestinationStation()),
                train.getCreatedAt(),
                train.getUpdatedAt());
    }
}
