package com.trainconcierge.dto;

import com.trainconcierge.entity.Hotel;
import com.trainconcierge.entity.HotelAvailabilityStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * Full hotel representation returned by the hotel APIs.
 *
 * <p>{@code amenities} is returned as a list parsed from the stored
 * comma-separated value; it is an empty list when no amenities are recorded.
 * Any field that is {@code null} was simply not known and is never invented.</p>
 */
public record HotelResponse(
        Long id,
        String name,
        StationSummaryResponse station,
        String locality,
        BigDecimal distanceKm,
        BigDecimal pricePerNight,
        String currency,
        BigDecimal rating,
        HotelAvailabilityStatus availabilityStatus,
        List<String> amenities,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static HotelResponse from(Hotel hotel) {
        return new HotelResponse(
                hotel.getId(),
                hotel.getName(),
                hotel.getStation() == null ? null : StationSummaryResponse.from(hotel.getStation()),
                hotel.getLocality(),
                hotel.getDistanceKm(),
                hotel.getPricePerNight(),
                hotel.getCurrency(),
                hotel.getRating(),
                hotel.getAvailabilityStatus(),
                parseAmenities(hotel.getAmenities()),
                hotel.isActive(),
                hotel.getCreatedAt(),
                hotel.getUpdatedAt());
    }

    static List<String> parseAmenities(String amenities) {
        if (amenities == null || amenities.isBlank()) {
            return List.of();
        }
        return Arrays.stream(amenities.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
