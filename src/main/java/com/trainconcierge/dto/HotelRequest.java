package com.trainconcierge.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * Create/update payload for a hotel (development/admin use).
 *
 * <p>Every commercial field is optional. When supplied it is validated:
 * a price must be &gt; 0, a distance must be within {@code [0, 1000]} km, a
 * rating must be within {@code [0, 5]} and a currency must be a 3-letter ISO
 * code. {@code stationId} links the hotel to a station and is optional.</p>
 *
 * <p>The availability status is never accepted from the client: created hotels
 * always get {@code AVAILABILITY_UNKNOWN}.</p>
 */
public record HotelRequest(

        @NotBlank(message = "name must not be blank")
        @Size(max = 150, message = "name must be at most 150 characters")
        String name,

        Long stationId,

        @Size(max = 150, message = "locality must be at most 150 characters")
        String locality,

        @DecimalMin(value = "0.0", message = "distanceKm must be zero or positive")
        @DecimalMax(value = "1000.0", message = "distanceKm must be at most 1000")
        BigDecimal distanceKm,

        @DecimalMin(value = "0.01", message = "pricePerNight must be greater than zero")
        @DecimalMax(value = "1000000.00", message = "pricePerNight is unrealistically large")
        BigDecimal pricePerNight,

        @Pattern(regexp = "^[A-Za-z]{3}$", message = "currency must be a 3-letter currency code")
        String currency,

        @DecimalMin(value = "0.0", message = "rating must be zero or positive")
        @DecimalMax(value = "5.0", message = "rating must be at most 5")
        BigDecimal rating,

        @Size(max = 255, message = "amenities must be at most 255 characters")
        String amenities,

        Boolean active
) {
}
