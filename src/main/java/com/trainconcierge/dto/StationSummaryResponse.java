package com.trainconcierge.dto;

import com.trainconcierge.entity.Station;

/**
 * Compact station view embedded inside train, stop and timetable responses.
 * Exists so that JPA relationships are never serialised recursively.
 */
public record StationSummaryResponse(
        Long id,
        String stationCode,
        String name,
        String city,
        String state,
        boolean active
) {

    public static StationSummaryResponse from(Station station) {
        return new StationSummaryResponse(
                station.getId(),
                station.getStationCode(),
                station.getName(),
                station.getCity(),
                station.getState(),
                station.isActive());
    }
}
