package com.trainconcierge.repository;

import com.trainconcierge.entity.JourneyLeg;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JourneyLegRepository extends JpaRepository<JourneyLeg, Long> {

    List<JourneyLeg> findByJourneyIdOrderByLegSequenceAsc(Long journeyId);
}
