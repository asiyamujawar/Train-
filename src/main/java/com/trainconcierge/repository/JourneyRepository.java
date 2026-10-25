package com.trainconcierge.repository;

import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.JourneyStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JourneyRepository extends JpaRepository<Journey, Long> {

    Optional<Journey> findByJourneyReference(String journeyReference);

    boolean existsByJourneyReference(String journeyReference);

    Page<Journey> findByPassengerId(Long passengerId, Pageable pageable);

    @Query("""
            select j from Journey j
            where (:passengerId is null or j.passenger.id = :passengerId)
              and (:journeyReference is null or lower(j.journeyReference) like lower(concat('%', :journeyReference, '%')))
              and (:journeyDate is null or j.journeyDate = :journeyDate)
              and (:status is null or j.status = :status)
            """)
    Page<Journey> search(@Param("passengerId") Long passengerId,
                         @Param("journeyReference") String journeyReference,
                         @Param("journeyDate") LocalDate journeyDate,
                         @Param("status") JourneyStatus status,
                         Pageable pageable);

    /**
     * Journeys whose travel date falls inside the Phase 5 impact-analysis window,
     * with their legs and leg trains fetched so the analysis does not trigger
     * N+1 queries. Ordered deterministically.
     */
    @Query("""
            select distinct j from Journey j
            join fetch j.legs l
            join fetch l.train
            where j.journeyDate between :from and :to
            order by j.id asc
            """)
    List<Journey> findJourneysInDateRangeWithLegs(@Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);
}
