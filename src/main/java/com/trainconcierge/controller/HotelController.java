package com.trainconcierge.controller;

import com.trainconcierge.dto.HotelRecommendationResponse;
import com.trainconcierge.dto.HotelRecommendationRunResponse;
import com.trainconcierge.dto.HotelRequest;
import com.trainconcierge.dto.HotelResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.service.HotelRecommendationService;
import com.trainconcierge.service.HotelScoringService;
import com.trainconcierge.service.HotelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.net.URI;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hotel master-data and hotel-recommendation endpoints (Phase 7). These are
 * unauthenticated development endpoints, consistent with the earlier phases.
 *
 * <p>A hotel recommendation is <strong>not a booking</strong>: this module never
 * claims that a room is available, that a price is guaranteed or that anything
 * has been reserved. Every hotel carries an explicit availability status and the
 * generated explanations say so.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Hotels", description = "Hotels near stations and deterministic overnight-stay suggestions for disrupted journeys.")
public class HotelController {

    private final HotelService hotelService;
    private final HotelRecommendationService hotelRecommendationService;

    public HotelController(HotelService hotelService, HotelRecommendationService hotelRecommendationService) {
        this.hotelService = hotelService;
        this.hotelRecommendationService = hotelRecommendationService;
    }

    @Operation(summary = "Create a hotel record (development/admin use)")
    @PostMapping("/hotels")
    public ResponseEntity<HotelResponse> create(@Valid @RequestBody HotelRequest request) {
        HotelResponse created = hotelService.create(request);

        return ResponseEntity
                .created(URI.create("/api/v1/hotels/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Search hotels with optional filters, pagination and sorting")
    @GetMapping("/hotels")
    public PageResponse<HotelResponse> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long stationId,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) BigDecimal maxDistanceKm,
            @RequestParam(required = false) String amenity,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        return hotelService.search(name, stationId, currency, active, maxPrice, minRating, maxDistanceKm, amenity,
                pageable);
    }

    @Operation(summary = "Get a hotel by id")
    @GetMapping("/hotels/{hotelId}")
    public HotelResponse getById(@PathVariable Long hotelId) {
        return hotelService.getById(hotelId);
    }

    @Operation(summary = "Evaluate whether a journey is likely to need an overnight stay, with ranked hotels")
    @GetMapping("/journeys/{journeyId}/hotel-recommendations")
    public HotelRecommendationResponse getForJourney(
            @PathVariable Long journeyId,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) BigDecimal maxDistanceKm,
            @RequestParam(required = false) String amenity) {

        return HotelRecommendationResponse.from(hotelRecommendationService.evaluate(journeyId,
                filters(maxPrice, currency, minRating, maxDistanceKm, amenity)));
    }

    @Operation(summary = "Rerun the overnight-stay evaluation for a journey and persist the result")
    @PostMapping("/journeys/{journeyId}/hotel-recommendations/run")
    public HotelRecommendationRunResponse runForJourney(
            @PathVariable Long journeyId,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) BigDecimal minRating,
            @RequestParam(required = false) BigDecimal maxDistanceKm,
            @RequestParam(required = false) String amenity) {

        return HotelRecommendationRunResponse.from(journeyId, hotelRecommendationService.runForJourney(journeyId,
                filters(maxPrice, currency, minRating, maxDistanceKm, amenity)));
    }

    private static HotelScoringService.HotelFilters filters(BigDecimal maxPrice,
                                                            String currency,
                                                            BigDecimal minRating,
                                                            BigDecimal maxDistanceKm,
                                                            String amenity) {
        return new HotelScoringService.HotelFilters(maxPrice, currency, minRating, maxDistanceKm, amenity);
    }
}
