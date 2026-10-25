package com.trainconcierge.controller;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.RecommendationRunResponse;
import com.trainconcierge.dto.TrainRecommendationResponse;
import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.service.AlternativeRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alternative-train recommendation endpoints (Phase 6). These are
 * unauthenticated development endpoints, consistent with the earlier phases.
 *
 * <p>The list endpoints return the AVAILABLE recommendations by default; pass
 * {@code recommendationStatus=EXPIRED} (or any other value) to inspect the
 * preserved history. The run endpoints recompute recommendations and are
 * idempotent: repeated calls do not create duplicates.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Recommendations", description = "Deterministic alternative-train suggestions for affected journeys.")
public class RecommendationController {

    private final AlternativeRecommendationService alternativeRecommendationService;

    public RecommendationController(AlternativeRecommendationService alternativeRecommendationService) {
        this.alternativeRecommendationService = alternativeRecommendationService;
    }

    @Operation(summary = "List the alternative-train recommendations for one journey (paginated)")
    @GetMapping("/journeys/{journeyId}/recommendations")
    public PageResponse<TrainRecommendationResponse> listForJourney(
            @PathVariable Long journeyId,
            @RequestParam(required = false) RecommendationStatus recommendationStatus,
            @PageableDefault(size = 20, sort = {"score", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return alternativeRecommendationService.listByJourney(journeyId, recommendationStatus, pageable);
    }

    @Operation(summary = "List the alternative-train recommendations produced for one impact (paginated)")
    @GetMapping("/impacts/{impactId}/recommendations")
    public PageResponse<TrainRecommendationResponse> listForImpact(
            @PathVariable Long impactId,
            @RequestParam(required = false) RecommendationStatus recommendationStatus,
            @PageableDefault(size = 20, sort = {"score", "id"}, direction = Sort.Direction.ASC)
            Pageable pageable) {
        return alternativeRecommendationService.listByImpact(impactId, recommendationStatus, pageable);
    }

    @Operation(summary = "Recompute the alternative-train recommendations for one impact")
    @PostMapping("/impacts/{impactId}/recommendations/run")
    public RecommendationRunResponse runForImpact(@PathVariable Long impactId) {
        return RecommendationRunResponse.from(alternativeRecommendationService.runForImpact(impactId));
    }

    @Operation(summary = "Recompute recommendations for the ongoing impacts in the configured window (bounded batch)")
    @PostMapping("/recommendations/run")
    public RecommendationRunResponse runAll() {
        return RecommendationRunResponse.from(alternativeRecommendationService.runAll());
    }
}
