package com.trainconcierge.service;

import com.trainconcierge.config.TrainConciergeProperties;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.TrainRecommendationResponse;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.entity.TrainRecommendation;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.JourneyRepository;
import com.trainconcierge.repository.TrainRecommendationRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Facade for the Phase 6 alternative-train recommendations: read endpoints and
 * the bounded batch run.
 *
 * <p>Generation itself lives in {@link ImpactRecommendationService}, which
 * reconciles each impact in its own transaction. The batch run therefore keeps
 * going when a single impact fails, and the failure is reported as a count.</p>
 */
@Service
public class AlternativeRecommendationService {

    private static final Logger log = LoggerFactory.getLogger(AlternativeRecommendationService.class);

    private final ImpactRecommendationService impactRecommendationService;
    private final JourneyImpactRepository journeyImpactRepository;
    private final JourneyRepository journeyRepository;
    private final TrainRecommendationRepository trainRecommendationRepository;
    private final TrainConciergeProperties properties;
    private final Clock clock;

    public AlternativeRecommendationService(ImpactRecommendationService impactRecommendationService,
                                            JourneyImpactRepository journeyImpactRepository,
                                            JourneyRepository journeyRepository,
                                            TrainRecommendationRepository trainRecommendationRepository,
                                            TrainConciergeProperties properties,
                                            Clock clock) {
        this.impactRecommendationService = impactRecommendationService;
        this.journeyImpactRepository = journeyImpactRepository;
        this.journeyRepository = journeyRepository;
        this.trainRecommendationRepository = trainRecommendationRepository;
        this.properties = properties;
        this.clock = clock;
    }

    // ------------------------------------------------------------------
    // Generation
    // ------------------------------------------------------------------

    /** Recomputes the recommendations of a single impact. */
    public RecommendationRunResult runForImpact(Long impactId) {
        return impactRecommendationService.reconcileImpact(impactId);
    }

    /**
     * Bounded batch run over the ongoing impacts whose journey date falls inside
     * the configured window. At most {@code max-impacts-per-run} impacts are
     * reconciled per cycle, in ascending impact id order, so the work is
     * deterministic and bounded.
     */
    public RecommendationRunResult runAll() {
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(properties.getImpact().getLookbackDays());
        LocalDate to = today.plusDays(properties.getImpact().getForwardDays());

        int maxImpacts = properties.getRecommendation().getMaxImpactsPerRun();
        Pageable limit = PageRequest.of(0, Math.max(maxImpacts, 1));
        List<JourneyImpact> impacts = journeyImpactRepository
                .findActiveImpactsInJourneyDateRange(ImpactState.ACTIVE, from, to, limit);

        RecommendationRunResult total = RecommendationRunResult.empty();
        for (JourneyImpact impact : impacts) {
            try {
                total = total.merge(impactRecommendationService.reconcileImpact(impact.getId()));
            } catch (RuntimeException ex) {
                log.error("Failed to build recommendations for impact {}: {}", impact.getId(), ex.getMessage());
                RecommendationRunResult failure = new RecommendationRunResult(
                        1, 0, 0, 0, 0, 0, 0, 1, new LinkedHashMap<>(), "IMPACT_FAILED");
                total = total.merge(failure);
            }
        }

        if (log.isInfoEnabled()) {
            log.info("Recommendation run finished: impactsInspected={}, candidatesEvaluated={}, created={}, "
                            + "updated={}, expired={}, unchanged={}, returned={}, failures={}",
                    total.impactsInspected(), total.candidatesEvaluated(), total.recommendationsCreated(),
                    total.recommendationsUpdated(), total.recommendationsExpired(),
                    total.recommendationsUnchanged(), total.recommendationsReturned(), total.failures());
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<TrainRecommendationResponse> listByJourney(Long journeyId,
                                                                   RecommendationStatus status,
                                                                   Pageable pageable) {
        if (!journeyRepository.existsById(journeyId)) {
            throw new ResourceNotFoundException("Journey not found with id: " + journeyId);
        }
        Page<TrainRecommendation> page = trainRecommendationRepository.search(
                journeyId, null, status == null ? RecommendationStatus.AVAILABLE : status, pageable);
        return PageResponse.from(page, TrainRecommendationResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainRecommendationResponse> listByImpact(Long impactId,
                                                                  RecommendationStatus status,
                                                                  Pageable pageable) {
        if (!journeyImpactRepository.existsById(impactId)) {
            throw new ResourceNotFoundException("Journey impact not found with id: " + impactId);
        }
        Page<TrainRecommendation> page = trainRecommendationRepository.search(
                null, impactId, status == null ? RecommendationStatus.AVAILABLE : status, pageable);
        return PageResponse.from(page, TrainRecommendationResponse::from);
    }
}
