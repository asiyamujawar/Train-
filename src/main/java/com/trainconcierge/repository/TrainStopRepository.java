package com.trainconcierge.repository;

import com.trainconcierge.entity.TrainStop;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainStopRepository extends JpaRepository<TrainStop, Long> {

    /** Stops of a train, ordered, with the station fetched to avoid N+1. */
    @Query("""
            select ts from TrainStop ts
            join fetch ts.station
            where ts.train.id = :trainId
            order by ts.stopSequence asc
            """)
    List<TrainStop> findStopsWithStation(@Param("trainId") Long trainId);

    Optional<TrainStop> findByIdAndTrainId(Long id, Long trainId);

    boolean existsByTrainIdAndStopSequence(Long trainId, Integer stopSequence);

    boolean existsByTrainIdAndStopSequenceAndIdNot(Long trainId, Integer stopSequence, Long id);

    boolean existsByTrainIdAndStationId(Long trainId, Long stationId);

    boolean existsByTrainIdAndStationIdAndIdNot(Long trainId, Long stationId, Long id);

    /**
     * Timetable search: returns one row per active train that stops at both
     * requested stations, in the requested order (source sequence strictly
     * lower than destination sequence). A single query is used so there is no
     * N+1 problem.
     *
     * <p>Row layout:
     * {@code [Train, sourceStation, destinationStation, sourceDepartureTime,
     * sourceDayOffset, sourceDistanceKm, destinationArrivalTime,
     * destinationDayOffset, destinationDistanceKm]}.</p>
     */
    @Query("""
            select t, srcStation, dstStation,
                   src.departureTime, src.dayOffset, src.distanceFromSourceKm,
                   dst.arrivalTime, dst.dayOffset, dst.distanceFromSourceKm
            from Train t, TrainStop src, TrainStop dst, Station srcStation, Station dstStation
            where src.train = t
              and dst.train = t
              and t.sourceStation = srcStation
              and t.destinationStation = dstStation
              and src.station.stationCode = :sourceStationCode
              and dst.station.stationCode = :destinationStationCode
              and src.stopSequence < dst.stopSequence
              and t.active = true
              and src.station.active = true
              and dst.station.active = true
            order by src.departureTime asc, t.trainNumber asc
            """)
    List<Object[]> searchTimetables(@Param("sourceStationCode") String sourceStationCode,
                                    @Param("destinationStationCode") String destinationStationCode);
}
