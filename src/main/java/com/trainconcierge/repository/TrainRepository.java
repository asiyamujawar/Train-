package com.trainconcierge.repository;

import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainRepository extends JpaRepository<Train, Long> {

    Optional<Train> findByTrainNumber(String trainNumber);

    boolean existsByTrainNumber(String trainNumber);

    boolean existsByTrainNumberAndIdNot(String trainNumber, Long id);

    /** Single train together with both stations, fetched in one query (no N+1). */
    @Query("""
            select t from Train t
            join fetch t.sourceStation
            join fetch t.destinationStation
            where t.id = :id
            """)
    Optional<Train> findDetailById(@Param("id") Long id);

    @Query("""
            select t from Train t
            join fetch t.sourceStation
            join fetch t.destinationStation
            where t.trainNumber = :trainNumber
            """)
    Optional<Train> findDetailByTrainNumber(@Param("trainNumber") String trainNumber);

    /**
     * Filtered, paginated train search. The two station associations are fetched
     * eagerly so the response can be built without extra per-row queries.
     */
    @Query(value = """
            select t from Train t
            join fetch t.sourceStation
            join fetch t.destinationStation
            where (:trainNumber is null or lower(t.trainNumber) like lower(concat('%', :trainNumber, '%')))
              and (:name is null or lower(t.name) like lower(concat('%', :name, '%')))
              and (:trainType is null or t.trainType = :trainType)
              and (:sourceStationId is null or t.sourceStation.id = :sourceStationId)
              and (:destinationStationId is null or t.destinationStation.id = :destinationStationId)
              and (:active is null or t.active = :active)
            """,
            countQuery = """
            select count(t) from Train t
            where (:trainNumber is null or lower(t.trainNumber) like lower(concat('%', :trainNumber, '%')))
              and (:name is null or lower(t.name) like lower(concat('%', :name, '%')))
              and (:trainType is null or t.trainType = :trainType)
              and (:sourceStationId is null or t.sourceStation.id = :sourceStationId)
              and (:destinationStationId is null or t.destinationStation.id = :destinationStationId)
              and (:active is null or t.active = :active)
            """)
    Page<Train> search(@Param("trainNumber") String trainNumber,
                       @Param("name") String name,
                       @Param("trainType") TrainType trainType,
                       @Param("sourceStationId") Long sourceStationId,
                       @Param("destinationStationId") Long destinationStationId,
                       @Param("active") Boolean active,
                       Pageable pageable);
}
