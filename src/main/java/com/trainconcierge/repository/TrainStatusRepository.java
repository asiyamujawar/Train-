package com.trainconcierge.repository;

import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainStatusRepository extends JpaRepository<TrainStatus, Long> {

    Optional<TrainStatus> findByTrainIdAndServiceDate(Long trainId, LocalDate serviceDate);

    boolean existsByTrainIdAndServiceDate(Long trainId, LocalDate serviceDate);

    /**
     * Paginated status search with optional filters. The train is fetched
     * eagerly because every response includes the train number and name.
     */
    @Query(value = """
            select ts from TrainStatus ts
            join fetch ts.train
            left join fetch ts.currentStation
            where (:trainId is null or ts.train.id = :trainId)
              and (:serviceDate is null or ts.serviceDate = :serviceDate)
              and (:status is null or ts.status = :status)
              and (:minDelayMinutes is null or ts.delayMinutes >= :minDelayMinutes)
            """,
            countQuery = """
            select count(ts) from TrainStatus ts
            where (:trainId is null or ts.train.id = :trainId)
              and (:serviceDate is null or ts.serviceDate = :serviceDate)
              and (:status is null or ts.status = :status)
              and (:minDelayMinutes is null or ts.delayMinutes >= :minDelayMinutes)
            """)
    Page<TrainStatus> search(@Param("trainId") Long trainId,
                             @Param("serviceDate") LocalDate serviceDate,
                             @Param("status") TrainRunningStatus status,
                             @Param("minDelayMinutes") Integer minDelayMinutes,
                             Pageable pageable);

    /**
     * All current statuses whose service date falls inside the monitoring window,
     * used by the Phase 4 disruption monitor. Deterministically ordered so runs
     * behave the same way every time.
     */
    @Query("""
            select ts from TrainStatus ts
            join fetch ts.train
            where ts.serviceDate between :from and :to
            order by ts.serviceDate asc, ts.train.id asc
            """)
    List<TrainStatus> findStatusesInServiceDateRange(@Param("from") LocalDate from,
                                                     @Param("to") LocalDate to);
}
