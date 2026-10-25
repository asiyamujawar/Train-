package com.trainconcierge.dto;

import com.trainconcierge.entity.TrainType;
import java.time.LocalTime;

/**
 * One train matched by a source/destination timetable search. Times are the
 * scheduled departure from the source stop and the scheduled arrival at the
 * destination stop; either may be null when the timetable does not define it,
 * in which case no value is invented.
 */
public record TimetableSearchResult(
        String trainNumber,
        String name,
        TrainType trainType,
        StationSummaryResponse sourceStation,
        StationSummaryResponse destinationStation,
        LocalTime departureTime,
        Integer departureDayOffset,
        LocalTime arrivalTime,
        Integer arrivalDayOffset,
        Integer distanceKm
) {
}
