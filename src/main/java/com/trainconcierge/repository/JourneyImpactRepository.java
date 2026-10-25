package com.trainconcierge.repository;

import com.trainconcierge.entity.ImpactSeverity;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.JourneyImpact;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JourneyImpactRepository extends JpaRepository<JourneyImpact, Long> {

    /** Ongoing impacts of one journey, oldest first. */
    List<JourneyImpact> findByJourneyIdAndImpactStateOrderByDetectedAtAscIdAsc(Long journeyId, ImpactState state);

    /** Journey ids that currently have at least one ongoing impact. */
    @Query("select distinct i.journey.id from JourneyImpact i where i.impactState = :state")
    List<Long> findJourneyIdsByImpactState(@Param("state") ImpactState state);

    /**
     * Paginated impact search with optional filters. Journey, leg, leg train and
     * disruption event are fetched eagerly because every response includes them,
     * so the list endpoint does not trigger N+1 queries.
     */
    @Query(value = """
            select i from JourneyImpact i
            join fetch i.journey
            join fetch i.disruptionEvent e
            join fetch e.train
            left join fetch i.journeyLeg l
            left join fetch l.train
            where (:journeyId is null or i.journey.id = :journeyId)
              and (:disruptionEventId is null or i.disruptionEvent.id = :disruptionEventId)
              and (:impactType is null or i.impactType = :impactType)
              and (:severity is null or i.severity = :severity)
              and (:impactState is null or i.impactState = :impactState)
              and (:minDelayMinutes is null or i.delayMinutes >= :minDelayMinutes)
            """,
            countQuery = """
            select count(i) from JourneyImpact i
            where (:journeyId is null or i.journey.id = :journeyId)
              and (:disruptionEventId is null or i.disruptionEvent.id = :disruptionEventId)
              and (:impactType is null or i.impactType = :impactType)
              and (:severity is null or i.severity = :severity)
              and (:impactState is null or i.impactState = :impactState)
              and (:minDelayMinutes is null or i.delayMinutes >= :minDelayMinutes)
            """)
    Page<JourneyImpact> search(@Param("journeyId") Long journeyId,
                               @Param("disruptionEventId") Long disruptionEventId,
                               @Param("impactType") ImpactType impactType,
                               @Param("severity") ImpactSeverity severity,
                               @Param("impactState") ImpactState impactState,
                               @Param("minDelayMinutes") Integer minDelayMinutes,
                               Pageable pageable);
}
