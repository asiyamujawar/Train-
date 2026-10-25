package com.trainconcierge.dto;

import com.trainconcierge.entity.TrainStop;
import java.time.LocalTime;

/** One timetable entry of a train, with the station as a summary. */
public record TrainStopResponse(
        Long id,
        Long trainId,
        StationSummaryResponse station,
        Integer stopSequence,
        LocalTime arrivalTime,
        LocalTime departureTime,
        Integer dayOffset,
        Integer distanceFromSourceKm
) {

    /**
     * @param trainId supplied by the caller so that the lazy train association
     *                does not have to be initialised just to read its id
     */
    public static TrainStopResponse from(TrainStop stop, Long trainId) {
        return new TrainStopResponse(
                stop.getId(),
                trainId,
                StationSummaryResponse.from(stop.getStation()),
                stop.getStopSequence(),
                stop.getArrivalTime(),
                stop.getDepartureTime(),
                stop.getDayOffset(),
                stop.getDistanceFromSourceKm());
    }
}
