package com.trainconcierge.controller;

import com.trainconcierge.dto.JourneyRequest;
import com.trainconcierge.dto.JourneyResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.entity.JourneyStatus;
import com.trainconcierge.service.JourneyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/journeys")
@Tag(name = "Journeys", description = "Journey planning and itinerary management")
public class JourneyController {

    private final JourneyService journeyService;

    public JourneyController(JourneyService journeyService) {
        this.journeyService = journeyService;
    }

    @Operation(summary = "Create a journey with one or more legs")
    @PostMapping
    public ResponseEntity<JourneyResponse> create(@Valid @RequestBody JourneyRequest request) {
        JourneyResponse created = journeyService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/journeys/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Get a journey by id")
    @GetMapping("/{id}")
    public JourneyResponse getById(@PathVariable Long id) {
        return journeyService.getById(id);
    }

    @Operation(summary = "Get a journey by reference")
    @GetMapping("/reference/{reference}")
    public JourneyResponse getByReference(@PathVariable String reference) {
        return journeyService.getByReference(reference);
    }

    @Operation(summary = "Search journeys with optional filters")
    @GetMapping
    public PageResponse<JourneyResponse> search(
            @RequestParam(required = false) Long passengerId,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) LocalDate journeyDate,
            @RequestParam(required = false) JourneyStatus status,
            @PageableDefault(size = 20, sort = "journeyDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return journeyService.search(passengerId, reference, journeyDate, status, pageable);
    }

    @Operation(summary = "List journeys for a specific passenger")
    @GetMapping("/passenger/{passengerId}")
    public PageResponse<JourneyResponse> listByPassenger(
            @PathVariable Long passengerId,
            @PageableDefault(size = 20, sort = "journeyDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return journeyService.listByPassenger(passengerId, pageable);
    }

    @Operation(summary = "Update a journey itinerary")
    @PutMapping("/{id}")
    public JourneyResponse updateItinerary(@PathVariable Long id, @Valid @RequestBody JourneyRequest request) {
        return journeyService.updateItinerary(id, request);
    }

    @Operation(summary = "Cancel a journey")
    @PatchMapping("/{id}/cancel")
    public JourneyResponse cancel(@PathVariable Long id) {
        return journeyService.cancel(id);
    }
}
