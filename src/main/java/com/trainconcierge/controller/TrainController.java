package com.trainconcierge.controller;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.TrainRequest;
import com.trainconcierge.dto.TrainResponse;
import com.trainconcierge.entity.TrainType;
import com.trainconcierge.service.TrainService;
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

/** Train master-data endpoints. */
@RestController
@RequestMapping("/api/v1/trains")
@Tag(name = "Trains", description = "Railway train master data")
public class TrainController {

    private final TrainService trainService;

    public TrainController(TrainService trainService) {
        this.trainService = trainService;
    }

    @Operation(summary = "Create a train")
    @PostMapping
    public ResponseEntity<TrainResponse> create(@Valid @RequestBody TrainRequest request) {
        TrainResponse created = trainService.create(request);

        return ResponseEntity
                .created(URI.create("/api/v1/trains/" + created.id()))
                .body(created);
    }

    @Operation(summary = "Get a train by id, including its source and destination stations")
    @GetMapping("/{id}")
    public TrainResponse getById(@PathVariable Long id) {
        return trainService.getById(id);
    }

    @Operation(summary = "Get a train by its unique train number")
    @GetMapping("/number/{trainNumber}")
    public TrainResponse getByTrainNumber(@PathVariable String trainNumber) {
        return trainService.getByTrainNumber(trainNumber);
    }

    @Operation(summary = "Search trains with optional filters, pagination and sorting")
    @GetMapping
    public PageResponse<TrainResponse> search(
            @RequestParam(required = false) String trainNumber,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) TrainType trainType,
            @RequestParam(required = false) Long sourceStationId,
            @RequestParam(required = false) Long destinationStationId,
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 20, sort = "trainNumber", direction = Sort.Direction.ASC) Pageable pageable) {

        return trainService.search(trainNumber, name, trainType, sourceStationId,
                destinationStationId, active, pageable);
    }

    @Operation(summary = "Update an existing train")
    @PutMapping("/{id}")
    public TrainResponse update(@PathVariable Long id, @Valid @RequestBody TrainRequest request) {
        return trainService.update(id, request);
    }

    @Operation(summary = "Deactivate a train (soft delete)")
    @PatchMapping("/{id}/deactivate")
    public TrainResponse deactivate(@PathVariable Long id) {
        return trainService.deactivate(id);
    }
}
