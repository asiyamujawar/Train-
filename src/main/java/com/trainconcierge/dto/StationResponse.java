package com.trainconcierge.dto;

import com.trainconcierge.entity.Station;
import java.time.OffsetDateTime;

/** Full station representation returned by the station APIs. */
public record StationResponse(
        Long id,
        String stationCode,
        String name,
        String city,
        String state,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static StationResponse from(Station station) {
        return new StationResponse(
                station.getId(),
                station.getStationCode(),
                station.getName(),
                station.getCity(),
                station.getState(),
                station.isActive(),
                station.getCreatedAt(),
                station.getUpdatedAt());
    }
}
