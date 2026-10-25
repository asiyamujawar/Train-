package com.trainconcierge.repository;

import com.trainconcierge.entity.Station;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StationRepository extends JpaRepository<Station, Long> {

    Optional<Station> findByStationCode(String stationCode);

    boolean existsByStationCode(String stationCode);

    boolean existsByStationCodeAndIdNot(String stationCode, Long id);

    /**
     * Filtered, paginated station search. Every parameter is optional: a null
     * value simply excludes that filter. Filtering happens in the database so
     * the full table is never loaded into memory.
     */
    @Query("""
            select s from Station s
            where (:name is null or lower(s.name) like lower(concat('%', :name, '%')))
              and (:city is null or lower(s.city) like lower(concat('%', :city, '%')))
              and (:stationCode is null or lower(s.stationCode) like lower(concat('%', :stationCode, '%')))
              and (:active is null or s.active = :active)
            """)
    Page<Station> search(@Param("name") String name,
                         @Param("city") String city,
                         @Param("stationCode") String stationCode,
                         @Param("active") Boolean active,
                         Pageable pageable);
}
