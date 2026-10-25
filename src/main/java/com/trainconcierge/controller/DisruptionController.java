package com.trainconcierge.controller;

import com.trainconcierge.dto.DisruptionEventResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.EventState;
import com.trainconcierge.service.DisruptionEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
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
 * Read and acknowledgement endpoints for automatically detected disruption events.
 *
 * <p>These endpoints are currently unauthenticated development endpoints.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Disruptions", description = "Disruption events detected from simulated running statuses.")
public class DisruptionController {

    private final DisruptionEventService disruptionEventService;

    public DisruptionController(DisruptionEventService disruptionEventService) {
        this.disruptionEventService = disruptionEventService;
    }

    @Operation(summary = "List disruption events with optional filters (paginated)")
    @GetMapping("/disruptions")
    public PageResponse<DisruptionEventResponse> list(
            @RequestParam(required = false) Long trainId,
            @RequestParam(required = false) LocalDate serviceDate,
            @RequestParam(required = false) DisruptionType disruptionType,
            @RequestParam(required = false) EventState eventState,
            @RequestParam(required = false) Integer minDelayMinutes,
            @PageableDefault(size = 20, sort = {"detectedAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return disruptionEventService.search(trainId, serviceDate, disruptionType, eventState,
                minDelayMinutes, pageable);
    }

    @Operation(summary = "Get one disruption event")
    @GetMapping("/disruptions/{id}")
    public DisruptionEventResponse get(@PathVariable Long id) {
        return disruptionEventService.getEvent(id);
    }

    @Operation(summary = "List disruption events for one train (paginated)")
    @GetMapping("/trains/{trainId}/disruptions")
    public PageResponse<DisruptionEventResponse> listForTrain(
            @PathVariable Long trainId,
            @RequestParam(required = false) LocalDate serviceDate,
            @RequestParam(required = false) EventState eventState,
            @PageableDefault(size = 20, sort = {"detectedAt", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return disruptionEventService.search(trainId, serviceDate, null, eventState, null, pageable);
    }

    @Operation(summary = "Acknowledge an ongoing disruption event without resolving it")
    @PostMapping("/disruptions/{id}/acknowledge")
    public DisruptionEventResponse acknowledge(@PathVariable Long id) {
        return disruptionEventService.acknowledge(id);
    }
}
