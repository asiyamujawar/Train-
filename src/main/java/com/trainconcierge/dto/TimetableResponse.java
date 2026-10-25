package com.trainconcierge.dto;

import java.util.List;

/**
 * A train's full timetable: the train itself (which already carries its source
 * and destination stations) plus its ordered stops.
 */
public record TimetableResponse(
        TrainResponse train,
        List<TrainStopResponse> stops
) {
}
