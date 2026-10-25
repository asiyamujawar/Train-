package com.trainconcierge.controller;

import com.trainconcierge.dto.ImpactAnalysisRunResponse;
import com.trainconcierge.service.ImpactAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for a journey impact-analysis cycle. It reuses exactly the same
 * service that a future scheduler would call, so repeated calls are idempotent
 * and it works even when the Phase 4 monitor is disabled. This is an
 * unauthenticated development endpoint.
 */
@RestController
@RequestMapping("/api/v1/impact-analysis")
@Tag(name = "Impact Analysis", description = "Manual trigger for journey impact analysis.")
public class ImpactAnalysisController {

    private final ImpactAnalysisService impactAnalysisService;

    public ImpactAnalysisController(ImpactAnalysisService impactAnalysisService) {
        this.impactAnalysisService = impactAnalysisService;
    }

    @Operation(summary = "Run one journey impact-analysis cycle immediately")
    @PostMapping("/run")
    public ImpactAnalysisRunResponse run() {
        return ImpactAnalysisRunResponse.from(impactAnalysisService.runAnalysis());
    }
}
