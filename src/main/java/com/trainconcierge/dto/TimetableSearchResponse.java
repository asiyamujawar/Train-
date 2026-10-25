package com.trainconcierge.dto;

import java.util.List;

/** Envelope for a timetable search, echoing the normalised query parameters. */
public record TimetableSearchResponse(
        String sourceStationCode,
        String destinationStationCode,
        int resultCount,
        List<TimetableSearchResult> results
) {
}
