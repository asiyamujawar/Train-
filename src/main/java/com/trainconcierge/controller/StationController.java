package com.trainconcierge.controller;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.StationRequest;
import com.trainconcierge.dto.StationResponse;
import com.trainconcierge.service.StationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
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

/** Station master-data endpoints. */
@RestController
@RequestMapping("/api/v1/stations")
@Tag(name = "Stations", description = "Railway station master data")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @Operation(summary = "Create a station")
    @PostMapping
    public ResponseEntity<StationResponse> create(@Valid @RequestBody StationRequest request) {
        StationResponse created = stationService.create(request);

        return ResponseEntity
                .created(URI.create("/api/v1/stations/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Get a station by id")
    @GetMapping("/{id}")
    public StationResponse getById(@PathVariable Long id) {
        return stationService.getById(id);
    }

    @Operation(summary = "Get a station by station code")
    @GetMapping("/code/{stationCode}")
    public StationResponse getByCode(@PathVariable String stationCode) {
        return stationService.getByCode(stationCode);
    }

    @Operation(summary = "Search stations with optional filters, pagination and sorting")
    @GetMapping
    public PageResponse<StationResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String stationCode,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "stationCode", direction = Sort.Direction.ASC) Pageable pageable) {

        return stationService.search(name, city, stationCode, active, pageable);
    }

    @Operation(summary = "Update an existing station")
    @PutMapping("/{id}")
    public StationResponse update(@PathVariable Long id, @Valid @RequestBody StationRequest request) {
        return stationService.update(id, request);
    }

    @Operation(summary = "Deactivate a station (soft delete)")
    @PatchMapping("/{id}/deactivate")
    public StationResponse deactivate(@PathVariable Long id) {
        return stationService.deactivate(id);
    }
}
