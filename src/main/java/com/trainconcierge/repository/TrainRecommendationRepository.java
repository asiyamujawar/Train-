package com.trainconcierge.repository;

import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.entity.TrainRecommendation;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TrainRecommendationRepository extends JpaRepository<TrainRecommendation, Long> {

    /**
     * The recommendations that currently hold a unique key for one impact, with
     * the entities needed to refresh them fetched eagerly. Ordered by the same
     * deterministic ranking used everywhere else (score, then train id).
     */
    @Query("""
            select r from TrainRecommendation r
            join fetch r.journey
            join fetch r.train
            left join fetch r.journeyLeg
            where r.journeyImpact.id = :impactId
              and r.recommendationStatus = :status
            order by r.score asc, r.train.id asc
            """)
    List<TrainRecommendation> findActiveForImpact(@Param("impactId") Long impactId,
                                                  @Param("status") RecommendationStatus status);

    /**
     * Paginated recommendation search with optional journey / impact / status
     * filters. Journey, leg, impact, disruption event and candidate train are
     * fetched eagerly because every response includes them, so the list
     * endpoints do not trigger N+1 queries.
     */
    @Query(value = """
            select r from TrainRecommendation r
            join fetch r.journey
            join fetch r.journeyImpact
            join fetch r.train
            left join fetch r.journeyLeg
            left join fetch r.disruptionEvent
            where (:journeyId is null or r.journey.id = :journeyId)
              and (:impactId is null or r.journeyImpact.id = :impactId)
              and (:status is null or r.recommendationStatus = :status)
            """,
            countQuery = """
            select count(r) from TrainRecommendation r
            where (:journeyId is null or r.journey.id = :journeyId)
              and (:impactId is null or r.journeyImpact.id = :impactId)
              and (:status is null or r.recommendationStatus = :status)
            """)
    Page<TrainRecommendation> search(@Param("journeyId") Long journeyId,
                                     @Param("impactId") Long impactId,
                                     @Param("status") RecommendationStatus status,
                                     Pageable pageable);
}
