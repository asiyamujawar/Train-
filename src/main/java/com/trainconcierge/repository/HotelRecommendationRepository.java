package com.trainconcierge.repository;

import com.trainconcierge.entity.HotelRecommendation;
import com.trainconcierge.entity.RecommendationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HotelRecommendationRepository extends JpaRepository<HotelRecommendation, Long> {

    /**
     * Rows of a journey in one status, with the hotel fetched so that a refresh
     * never triggers N+1 queries. Ordered deterministically by rank then id.
     */
    @Query("""
            select r from HotelRecommendation r
            join fetch r.hotel
            where r.journey.id = :journeyId
              and r.recommendationStatus = :status
            order by r.rankPosition asc, r.id asc
            """)
    List<HotelRecommendation> findForJourney(@Param("journeyId") Long journeyId,
                                             @Param("status") RecommendationStatus status);
}
