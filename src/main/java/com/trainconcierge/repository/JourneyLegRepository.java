package com.trainconcierge.repository;

import com.trainconcierge.entity.JourneyLeg;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JourneyLegRepository extends JpaRepository<JourneyLeg, Long> {

    List<JourneyLeg> findByJourneyIdOrderByLegSequenceAsc(Long journeyId);

    /**
     * Legs of a journey with train and both stations fetched, so impact analysis
     * can build explanations without triggering N+1 queries.
     */
    @Query("""
            select l from JourneyLeg l
            join fetch l.train
            join fetch l.sourceStation
            join fetch l.destinationStation
            where l.journey.id = :journeyId
            order by l.legSequence asc
            """)
    List<JourneyLeg> findLegsWithDetails(@Param("journeyId") Long journeyId);
}
