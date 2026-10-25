package com.trainconcierge.controller;

import com.trainconcierge.dto.TimetableResponse;
import com.trainconcierge.dto.TimetableSearchResponse;
import com.trainconcierge.service.TimetableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Timetable read endpoints. */
@RestController
@Validated
@Tag(name = "Timetables", description = "Train timetables and source-to-destination search")
public class TimetableController {

    private final TimetableService timetableService;

    public TimetableController(TimetableService timetableService) {
        this.timetableService = timetableService;
    }

    @Operation(summary = "Get the ordered timetable of a train")
    @GetMapping("/api/v1/trains/{trainId}/timetable")
    public TimetableResponse getTimetable(@PathVariable Long trainId) {
        return timetableService.getTimetable(trainId);
    }

    @Operation(summary = "Find active trains stopping at both stations, in the given order")
    @GetMapping("/api/v1/timetables/search")
    public TimetableSearchResponse search(
            @RequestParam @NotBlank(message = "sourceStationCode must not be blank") String sourceStationCode,
            @RequestParam @NotBlank(message = "destinationStationCode must not be blank") String destinationStationCode) {

        return timetableService.search(sourceStationCode, destinationStationCode);
    }
}
