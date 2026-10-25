package com.trainconcierge.controller;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.PassengerRequest;
import com.trainconcierge.dto.PassengerResponse;
import com.trainconcierge.service.PassengerService;
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

@RestController
@RequestMapping("/api/v1/passengers")
@Tag(name = "Passengers", description = "Passenger management")
public class PassengerController {

    private final PassengerService passengerService;

    public PassengerController(PassengerService passengerService) {
        this.passengerService = passengerService;
    }

    @Operation(summary = "Create a passenger")
    @PostMapping
    public ResponseEntity<PassengerResponse> create(@Valid @RequestBody PassengerRequest request) {
        PassengerResponse created = passengerService.create(request);
        return ResponseEntity
                .created(URI.create("/api/v1/passengers/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Get a passenger by id")
    @GetMapping("/{id}")
    public PassengerResponse getById(@PathVariable Long id) {
        return passengerService.getById(id);
    }

    @Operation(summary = "Search passengers with optional filters, pagination and sorting")
    @GetMapping
    public PageResponse<PassengerResponse> search(
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "fullName", direction = Sort.Direction.ASC) Pageable pageable) {

        return passengerService.search(fullName, email, active, pageable);
    }

    @Operation(summary = "Update an existing passenger")
    @PutMapping("/{id}")
    public PassengerResponse update(@PathVariable Long id, @Valid @RequestBody PassengerRequest request) {
        return passengerService.update(id, request);
    }

    @Operation(summary = "Deactivate a passenger")
    @PatchMapping("/{id}/deactivate")
    public PassengerResponse deactivate(@PathVariable Long id) {
        return passengerService.deactivate(id);
    }
}
