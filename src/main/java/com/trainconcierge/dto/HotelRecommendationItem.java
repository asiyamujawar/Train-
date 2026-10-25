package com.trainconcierge.dto;

/**
 * One ranked hotel suggestion inside a hotel-recommendation response.
 *
 * <p>A recommendation is <strong>not</strong> a booking: {@code reason} explains
 * why the hotel was included (proximity, price limit, rating, ...) and the
 * nested hotel carries the explicit availability status.</p>
 *
 * @param rank   1-based position in the deterministic ranking
 * @param hotel  the recommended hotel
 * @param reason human-readable justification derived from actual data
 */
public record HotelRecommendationItem(
        int rank,
        HotelResponse hotel,
        String reason
) {
}
