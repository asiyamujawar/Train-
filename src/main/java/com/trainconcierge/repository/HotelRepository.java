package com.trainconcierge.repository;

import com.trainconcierge.entity.Hotel;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HotelRepository extends JpaRepository<Hotel, Long> {

    boolean existsByNameAndStationId(String name, Long stationId);

    boolean existsByNameAndStationIsNull(String name);

    /**
     * Filtered, paginated hotel search. Every parameter is optional: a null
     * value excludes that filter.
     *
     * <p>Optional numeric filters ({@code maxPrice}, {@code minRating},
     * {@code maxDistanceKm}) keep hotels whose value is <strong>unknown</strong>
     * ({@code null}) as well as those that satisfy the bound, so a hotel is never
     * dropped merely because an optional field is missing. The categorical
     * filters ({@code currency}, {@code amenity}) require an actual match.</p>
     */
    @Query(value = """
            select h from Hotel h
            left join fetch h.station
            where (:name is null or lower(h.name) like lower(concat('%', :name, '%')))
              and (:stationId is null or h.station.id = :stationId)
              and (:currency is null or h.currency = :currency)
              and (:active is null or h.active = :active)
              and (:maxPrice is null or h.pricePerNight is null or h.pricePerNight <= :maxPrice)
              and (:minRating is null or h.rating is null or h.rating >= :minRating)
              and (:maxDistanceKm is null or h.distanceKm is null or h.distanceKm <= :maxDistanceKm)
              and (:amenity is null or lower(h.amenities) like lower(concat('%', :amenity, '%')))
            """,
            countQuery = """
            select count(h) from Hotel h
            where (:name is null or lower(h.name) like lower(concat('%', :name, '%')))
              and (:stationId is null or h.station.id = :stationId)
              and (:currency is null or h.currency = :currency)
              and (:active is null or h.active = :active)
              and (:maxPrice is null or h.pricePerNight is null or h.pricePerNight <= :maxPrice)
              and (:minRating is null or h.rating is null or h.rating >= :minRating)
              and (:maxDistanceKm is null or h.distanceKm is null or h.distanceKm <= :maxDistanceKm)
              and (:amenity is null or lower(h.amenities) like lower(concat('%', :amenity, '%')))
            """)
    Page<Hotel> search(@Param("name") String name,
                       @Param("stationId") Long stationId,
                       @Param("currency") String currency,
                       @Param("active") Boolean active,
                       @Param("maxPrice") BigDecimal maxPrice,
                       @Param("minRating") BigDecimal minRating,
                       @Param("maxDistanceKm") BigDecimal maxDistanceKm,
                       @Param("amenity") String amenity,
                       Pageable pageable);
}
