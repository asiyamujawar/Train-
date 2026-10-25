package com.trainconcierge.service;

import com.trainconcierge.entity.Hotel;
import com.trainconcierge.entity.HotelAvailabilityStatus;
import com.trainconcierge.entity.Station;
import java.math.BigDecimal;
import java.util.Comparator;
import org.springframework.stereotype.Service;

/**
 * Deterministic ranking and explanation for hotel recommendations.
 *
 * <p>Pure logic with no persistence, so it is unit-testable without a database.</p>
 *
 * <h2>Ranking rules (lower position = better)</h2>
 * <ol>
 *   <li>Hotels with a <strong>known price</strong> come before hotels without one.</li>
 *   <li>Results are <strong>grouped by currency</strong> (ISO code ascending,
 *       unknown currency last). This is deliberate: prices in different
 *       currencies must never be compared without a real conversion source.</li>
 *   <li>Within a currency group, hotels with a <strong>known distance</strong>
 *       come first, ordered by <strong>increasing distance</strong> (closest to
 *       the station first).</li>
 *   <li>Then by <strong>increasing price</strong> (only ever compared inside the
 *       same currency group, per rule 2).</li>
 *   <li>Then by <strong>decreasing rating</strong>.</li>
 *   <li>Finally by <strong>name</strong> then <strong>id</strong>, so the order
 *       is stable even for otherwise identical hotels.</li>
 * </ol>
 *
 * <p>Missing optional values never remove a hotel from the results; they only
 * move it later in the order.</p>
 */
@Service
public class HotelScoringService {

    /** Maximum length of a stored reason (matches {@code hotels.reason VARCHAR(500)}). */
    static final int MAX_REASON_LENGTH = 500;

    public Comparator<Hotel> ranking() {
        return Comparator
                .comparingInt((Hotel hotel) -> hotel.getPricePerNight() == null ? 1 : 0)
                .thenComparing((Hotel hotel) -> hotel.getCurrency(),
                        Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt((Hotel hotel) -> hotel.getDistanceKm() == null ? 1 : 0)
                .thenComparing((Hotel hotel) -> hotel.getDistanceKm(),
                        Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing((Hotel hotel) -> hotel.getPricePerNight(),
                        Comparator.nullsLast(BigDecimal::compareTo))
                .thenComparing((Hotel hotel) -> hotel.getRating(),
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Hotel::getName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Hotel::getId, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Builds the human-readable justification for one recommendation. Every
     * clause is derived from actual stored data; missing values are stated
     * explicitly rather than guessed.
     */
    public String explain(Hotel hotel, Station station, HotelFilters filters) {
        StringBuilder reason = new StringBuilder("Recommended because it is associated with ")
                .append(station.getName())
                .append(" (")
                .append(station.getStationCode())
                .append("), the station where the journey is disrupted");

        if (hotel.getDistanceKm() != null) {
            reason.append("; ").append(plain(hotel.getDistanceKm())).append(" km from the station");
        } else {
            reason.append("; distance from the station is not recorded");
        }

        if (hotel.getPricePerNight() != null) {
            reason.append("; ");
            if (hotel.getCurrency() != null) {
                reason.append(hotel.getCurrency()).append(' ');
            }
            reason.append(plain(hotel.getPricePerNight())).append(" per night");
            if (filters != null && filters.maxPrice() != null) {
                reason.append(" (within your limit of ").append(plain(filters.maxPrice())).append(')');
            }
        } else {
            reason.append("; nightly price is not recorded");
        }

        if (hotel.getRating() != null) {
            reason.append("; rating ").append(plain(hotel.getRating()));
        } else {
            reason.append("; rating is not recorded");
        }

        reason.append(hotel.getAvailabilityStatus() == HotelAvailabilityStatus.DEMO_DATA
                ? "; seeded demo data - availability is not confirmed and this is not a booking"
                : "; availability not verified - this is not a booking");

        if (hotel.getLocality() != null) {
            reason.append("; locality: ").append(hotel.getLocality());
        }

        return truncate(reason.toString());
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    static String truncate(String value) {
        return value.length() <= MAX_REASON_LENGTH
                ? value
                : value.substring(0, MAX_REASON_LENGTH);
    }

    /**
     * Optional filters applied to a hotel pool; any {@code null} element is
     * ignored. Filtering itself is performed by
     * {@code HotelRepository.search(...)} so there is a single source of truth;
     * this record only carries the values into that query and into the
     * explanations.
     */
    public record HotelFilters(
            BigDecimal maxPrice,
            String currency,
            BigDecimal minRating,
            BigDecimal maxDistanceKm,
            String amenity
    ) {

        public static HotelFilters none() {
            return new HotelFilters(null, null, null, null, null);
        }
    }
}
