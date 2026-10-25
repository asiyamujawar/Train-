package com.trainconcierge.controller;

import com.trainconcierge.dto.JourneyImpactResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.entity.ImpactSeverity;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.service.JourneyImpactService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints for the journey impacts produced by the Phase 5 impact
 * analysis. These endpoints are currently unauthenticated development endpoints.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Journey Impacts", description = "Journeys affected by active disruptions.")
public class JourneyImpactController {

    private final JourneyImpactService journeyImpactService;

    public JourneyImpactController(JourneyImpactService journeyImpactService) {
        this.journeyImpactService = journeyImpactService;
    }

    @Operation(summary = "List journey impacts with optional filters (paginated)")
    @GetMapping("/impacts")
    public PageResponse<JourneyImpactResponse> list(
            @RequestParam(required = false) Long journeyId,
            @RequestParam(required = false) Long disruptionEventId,
            @RequestParam(required = false) ImpactType impactType,
            @RequestParam(required = false) ImpactSeverity severity,
            @RequestParam(required = false) ImpactState impactState,
            @RequestParam(required = false) Integer minDelayMinutes,
            @PageableDefault(size = 20, sort = {"detectedAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return journeyImpactService.search(journeyId, disruptionEventId, impactType, severity, impactState,
                minDelayMinutes, pageable);
    }

    @Operation(summary = "Get one journey impact")
    @GetMapping("/impacts/{id}")
    public JourneyImpactResponse get(@PathVariable Long id) {
        return journeyImpactService.getImpact(id);
    }

    @Operation(summary = "List journey impacts for one journey (paginated)")
    @GetMapping("/journeys/{journeyId}/impacts")
    public PageResponse<JourneyImpactResponse> listForJourney(
            @PathVariable Long journeyId,
            @PageableDefault(size = 20, sort = {"detectedAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return journeyImpactService.listByJourney(journeyId, pageable);
    }
}
