package com.trainconcierge.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.trainconcierge.entity.Hotel;
import com.trainconcierge.entity.HotelAvailabilityStatus;
import com.trainconcierge.entity.Station;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the deterministic hotel ranking and explanation logic. No
 * database or Spring context is needed.
 */
class HotelScoringServiceTest {

    private final HotelScoringService scoringService = new HotelScoringService();

    @Test
    @DisplayName("hotels with a known price come first, then results are grouped by currency")
    void knownPriceFirstThenCurrencyGrouping() {
        Hotel noPrice = hotel("No Price", null, null, "4.9", "INR");
        Hotel usd = hotel("Usd Hotel", "100.00", "1.00", "4.0", "USD");
        Hotel inr = hotel("Inr Hotel", "200.00", "1.00", "4.0", "INR");

        List<Hotel> ordered = sorted(noPrice, usd, inr);

        assertThat(ordered).extracting(Hotel::getName).containsExactly("Inr Hotel", "Usd Hotel", "No Price");
    }

    @Test
    @DisplayName("within a currency group: distance, then price, then rating, then name")
    void distancePriceRatingNameOrdering() {
        Hotel closest = hotel("Bravo", "3000.00", "0.50", "4.0", "INR");
        Hotel samePointCheap = hotel("Alpha", "1000.00", "1.00", "4.0", "INR");
        Hotel samePointPricey = hotel("Charly", "2500.00", "1.00", "4.9", "INR");

        List<Hotel> ordered = sorted(closest, samePointPricey, samePointCheap);

        assertThat(ordered).extracting(Hotel::getName).containsExactly("Bravo", "Alpha", "Charly");
    }

    @Test
    @DisplayName("a hotel with an unknown distance is ordered after known distances but never removed")
    void unknownDistanceIsLastWithinCurrency() {
        Hotel known = hotel("Known", "1000.00", "2.00", "4.0", "INR");
        Hotel unknown = hotel("Unknown", "1000.00", null, "4.0", "INR");

        List<Hotel> ordered = sorted(unknown, known);

        assertThat(ordered).extracting(Hotel::getName).containsExactly("Known", "Unknown");
    }

    @Test
    @DisplayName("identical hotels are tie-broken by name and then by id")
    void identicalHotelsUseNameThenId() {
        Hotel beta = hotel("Zeta", "1000.00", "1.00", "4.0", "INR");
        Hotel alpha = hotel("Alpha", "1000.00", "1.00", "4.0", "INR");

        List<Hotel> ordered = sorted(beta, alpha);

        assertThat(ordered).extracting(Hotel::getName).containsExactly("Alpha", "Zeta");
    }

    @Test
    @DisplayName("the explanation states the distance, price, rating and that this is not a booking")
    void explanationForKnownData() {
        Hotel hotel = hotel("Demo Inn (fictional)", "4500.00", "0.80", "4.2", "INR");
        hotel.setAvailabilityStatus(HotelAvailabilityStatus.DEMO_DATA);
        hotel.setLocality("Fort");

        String reason = scoringService.explain(hotel, station(), HotelScoringService.HotelFilters.none());

        assertThat(reason).contains("0.8 km from the station");
        assertThat(reason).contains("INR 4500 per night");
        assertThat(reason).contains("rating 4.2");
        assertThat(reason).contains("seeded demo data");
        assertThat(reason).contains("not a booking");
        assertThat(reason).contains("Fort");
    }

    @Test
    @DisplayName("missing optional data is stated explicitly instead of being invented")
    void explanationForMissingData() {
        Hotel hotel = hotel("Demo Inn (fictional)", null, null, null, null);

        String reason = scoringService.explain(hotel, station(), HotelScoringService.HotelFilters.none());

        assertThat(reason).contains("distance from the station is not recorded");
        assertThat(reason).contains("nightly price is not recorded");
        assertThat(reason).contains("rating is not recorded");
        assertThat(reason).contains("availability not verified - this is not a booking");
    }

    @Test
    @DisplayName("a price limit is echoed in the explanation when it is applied")
    void explanationMentionsAppliedPriceLimit() {
        Hotel hotel = hotel("Demo Inn (fictional)", "1500.00", "1.00", "3.5", "INR");

        String reason = scoringService.explain(hotel, station(),
                new HotelScoringService.HotelFilters(new BigDecimal("2000"), null, null, null, null));

        assertThat(reason).contains("within your limit of 2000");
    }

    @Test
    @DisplayName("a reason longer than the column limit is truncated, never rejected")
    void reasonIsTruncatedToColumnLimit() {
        String longLocality = "L".repeat(600);

        String truncated = HotelScoringService.truncate(longLocality);

        assertThat(truncated).hasSize(HotelScoringService.MAX_REASON_LENGTH);
    }

    private List<Hotel> sorted(Hotel... hotels) {
        List<Hotel> list = new ArrayList<>(List.of(hotels));
        list.sort(scoringService.ranking());
        return list;
    }

    private Station station() {
        Station station = new Station();
        station.setStationCode("CSMT");
        station.setName("Chhatrapati Shivaji Maharaj Terminus");
        station.setCity("Mumbai");
        station.setActive(true);
        return station;
    }

    private Hotel hotel(String name, String price, String distance, String rating, String currency) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        hotel.setPricePerNight(price == null ? null : new BigDecimal(price));
        hotel.setDistanceKm(distance == null ? null : new BigDecimal(distance));
        hotel.setRating(rating == null ? null : new BigDecimal(rating));
        hotel.setCurrency(currency);
        hotel.setAvailabilityStatus(HotelAvailabilityStatus.AVAILABILITY_UNKNOWN);
        hotel.setActive(true);
        return hotel;
    }
}
