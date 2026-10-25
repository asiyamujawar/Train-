package com.trainconcierge.repository;

import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.EventState;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisruptionEventRepository extends JpaRepository<DisruptionEvent, Long> {

    /** Ongoing (ACTIVE or ACKNOWLEDGED) events for one train and service date, oldest first. */
    List<DisruptionEvent> findByTrainIdAndServiceDateAndEventStateInOrderByDetectedAtAscIdAsc(
            Long trainId, LocalDate serviceDate, Collection<EventState> states);

    Optional<DisruptionEvent> findByActiveKey(String activeKey);

    /**
     * Paginated disruption search with optional filters. The train is fetched
     * eagerly because every response includes the train number and name.
     */
    @Query(value = """
            select e from DisruptionEvent e
            join fetch e.train
            where (:trainId is null or e.train.id = :trainId)
              and (:serviceDate is null or e.serviceDate = :serviceDate)
              and (:type is null or e.disruptionType = :type)
              and (:state is null or e.eventState = :state)
              and (:minDelayMinutes is null or e.delayMinutes >= :minDelayMinutes)
            """,
            countQuery = """
            select count(e) from DisruptionEvent e
            where (:trainId is null or e.train.id = :trainId)
              and (:serviceDate is null or e.serviceDate = :serviceDate)
              and (:type is null or e.disruptionType = :type)
              and (:state is null or e.eventState = :state)
              and (:minDelayMinutes is null or e.delayMinutes >= :minDelayMinutes)
            """)
    Page<DisruptionEvent> search(@Param("trainId") Long trainId,
                                 @Param("serviceDate") LocalDate serviceDate,
                                 @Param("type") DisruptionType type,
                                 @Param("state") EventState state,
                                 @Param("minDelayMinutes") Integer minDelayMinutes,
                                 Pageable pageable);
}
