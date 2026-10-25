package com.trainconcierge.controller;

import com.trainconcierge.dto.MonitoringRunResponse;
import com.trainconcierge.service.DisruptionMonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for a disruption-monitoring cycle. It reuses exactly the same
 * service as the scheduled task, so repeated calls are idempotent. This is an
 * unauthenticated development endpoint.
 */
@RestController
@RequestMapping("/api/v1/monitoring")
@Tag(name = "Monitoring", description = "Manual trigger for the disruption monitor.")
public class MonitoringController {

    private final DisruptionMonitoringService disruptionMonitoringService;

    public MonitoringController(DisruptionMonitoringService disruptionMonitoringService) {
        this.disruptionMonitoringService = disruptionMonitoringService;
    }

    @Operation(summary = "Run one disruption-monitoring cycle immediately")
    @PostMapping("/run")
    public MonitoringRunResponse run() {
        return MonitoringRunResponse.from(disruptionMonitoringService.runMonitoring());
    }
}
