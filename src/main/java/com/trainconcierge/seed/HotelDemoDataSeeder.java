package com.trainconcierge.seed;

import com.trainconcierge.entity.Hotel;
import com.trainconcierge.entity.HotelAvailabilityStatus;
import com.trainconcierge.entity.Station;
import com.trainconcierge.repository.HotelRepository;
import com.trainconcierge.repository.StationRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a small set of <strong>fictional</strong> demo hotels for development and
 * Postman testing (Phase 7).
 *
 * <h2>Why a runtime seeder and not a migration?</h2>
 * <p>Fictional demo data must never reach a production database. It is therefore
 * kept out of a Flyway migration and inserted by this component, which is only
 * active while {@code trainconcierge.hotel.demo-seed-enabled} is {@code true}
 * (the default). Set it to {@code false}
 * ({@code TRAINCONCIERGE_HOTEL_DEMO_SEED_ENABLED=false}) in production to disable
 * seeding entirely.</p>
 *
 * <h2>Repeatability</h2>
 * <p>Each demo hotel is inserted only when a hotel with the same name is not
 * already linked to the same station, so restarting the application never
 * creates duplicates.</p>
 *
 * <h2>Honesty</h2>
 * <p>Every row is stored with {@link HotelAvailabilityStatus#DEMO_DATA}. The
 * names, prices, distances and ratings are illustrative only; they are not real
 * businesses and no availability is claimed. If a referenced station does not
 * exist, that hotel is skipped rather than given a fabricated location.</p>
 */
@Component
@ConditionalOnProperty(prefix = "trainconcierge.hotel", name = "demo-seed-enabled",
        havingValue = "true", matchIfMissing = true)
public class HotelDemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(HotelDemoDataSeeder.class);

    private static final String CURRENCY = "INR";

    private final HotelRepository hotelRepository;
    private final StationRepository stationRepository;

    public HotelDemoDataSeeder(HotelRepository hotelRepository, StationRepository stationRepository) {
        this.hotelRepository = hotelRepository;
        this.stationRepository = stationRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;
        int skipped = 0;

        for (DemoHotel demo : DEMO_HOTELS) {
            Station station = demo.stationCode() == null
                    ? null
                    : stationRepository.findByStationCode(demo.stationCode()).orElse(null);

            if (demo.stationCode() != null && station == null) {
                log.warn("Skipping demo hotel '{}': station '{}' is not present", demo.name(), demo.stationCode());
                skipped++;
                continue;
            }

            boolean exists = station == null
                    ? hotelRepository.existsByNameAndStationIsNull(demo.name())
                    : hotelRepository.existsByNameAndStationId(demo.name(), station.getId());

            if (exists) {
                skipped++;
                continue;
            }

            hotelRepository.save(toEntity(demo, station));
            created++;
        }

        log.info("Demo hotel seeding complete: {} created, {} already present (fictional demo data; "
                + "availability is never confirmed). Disable with "
                + "trainconcierge.hotel.demo-seed-enabled=false", created, skipped);
    }

    private Hotel toEntity(DemoHotel demo, Station station) {
        Hotel hotel = new Hotel();
        hotel.setName(demo.name());
        hotel.setStation(station);
        hotel.setLocality(demo.locality());
        hotel.setDistanceKm(demo.distanceKm());
        hotel.setPricePerNight(demo.pricePerNight());
        hotel.setCurrency(demo.pricePerNight() == null ? null : CURRENCY);
        hotel.setRating(demo.rating());
        hotel.setAmenities(demo.amenities());
        hotel.setAvailabilityStatus(HotelAvailabilityStatus.DEMO_DATA);
        hotel.setActive(true);
        return hotel;
    }

    /**
     * One fictional demo hotel. {@code stationCode} refers to a station seeded by
     * migration {@code V3__seed_railway_sample_data.sql}; when it is
     * {@code null} the hotel is intentionally not tied to a station.
     */
    private record DemoHotel(
            String name,
            String stationCode,
            String locality,
            BigDecimal distanceKm,
            BigDecimal pricePerNight,
            BigDecimal rating,
            String amenities
    ) {
    }

    private static final List<DemoHotel> DEMO_HOTELS = List.of(
            new DemoHotel("Demo Grand Mumbai Central (fictional)", "CSMT", "Fort, near CSMT",
                    new BigDecimal("0.80"), new BigDecimal("4500.00"), new BigDecimal("4.2"),
                    "WIFI,BREAKFAST,AIRPORT_TRANSFER"),
            new DemoHotel("Demo Budget Stay Mumbai (fictional)", "CSMT", "P D'Mello Road",
                    new BigDecimal("1.90"), new BigDecimal("1200.00"), new BigDecimal("3.1"), "WIFI"),
            // Deliberately missing price, rating and distance: demonstrates that a
            // hotel is still recommended (with the gaps stated) when optional data
            // is unknown.
            new DemoHotel("Demo Heritage Residency (fictional)", "CSMT", "Fort",
                    null, null, null, null),
            new DemoHotel("Demo Capital Inn Delhi (fictional)", "NDLS", "Paharganj",
                    new BigDecimal("1.20"), new BigDecimal("3000.00"), new BigDecimal("3.9"), "WIFI,BREAKFAST"),
            new DemoHotel("Demo Metro Suites Delhi (fictional)", "NDLS", "Near New Delhi station",
                    new BigDecimal("6.50"), new BigDecimal("7200.00"), new BigDecimal("4.6"), "WIFI,POOL,GYM"),
            new DemoHotel("Demo Deccan Comfort Pune (fictional)", "PUNE", "Near Pune Junction",
                    new BigDecimal("0.60"), new BigDecimal("2600.00"), new BigDecimal("4.0"), "WIFI,BREAKFAST"),
            new DemoHotel("Demo Koregaon Boutique Pune (fictional)", "PUNE", "Koregaon Park",
                    new BigDecimal("5.40"), new BigDecimal("5400.00"), new BigDecimal("4.4"), "WIFI,SPA"),
            new DemoHotel("Demo Solapur Transit Lodge (fictional)", "SUR", "Near Solapur Junction",
                    new BigDecimal("0.40"), new BigDecimal("900.00"), new BigDecimal("2.8"), null));
}
