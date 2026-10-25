package com.trainconcierge.controller;

import com.trainconcierge.dto.TrainStopRequest;
import com.trainconcierge.dto.TrainStopResponse;
import com.trainconcierge.service.TrainStopService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints for the ordered stops of a single train. */
@RestController
@RequestMapping("/api/v1/trains/{trainId}/stops")
@Tag(name = "Train stops", description = "Ordered stops of a train")
public class TrainStopController {

    private final TrainStopService trainStopService;

    public TrainStopController(TrainStopService trainStopService) {
        this.trainStopService = trainStopService;
    }

    @Operation(summary = "Add a stop to a train")
    @PostMapping
    public ResponseEntity<TrainStopResponse> addStop(@PathVariable Long trainId,
                                                     @Valid @RequestBody TrainStopRequest request) {
        TrainStopResponse created = trainStopService.addStop(trainId, request);

        return ResponseEntity
                .created(URI.create("/api/v1/trains/" + trainId + "/stops/" + created.id()))
                .body(created);
    }

    @Operation(summary = "List the stops of a train, ordered by stop sequence")
    @GetMapping
    public List<TrainStopResponse> listStops(@PathVariable Long trainId) {
        return trainStopService.listStops(trainId);
    }

    @Operation(summary = "Update a stop of a train")
    @PutMapping("/{stopId}")
    public TrainStopResponse updateStop(@PathVariable Long trainId,
                                        @PathVariable Long stopId,
                                        @Valid @RequestBody TrainStopRequest request) {
        return trainStopService.updateStop(trainId, stopId, request);
    }

    @Operation(summary = "Delete a stop of a train")
    @DeleteMapping("/{stopId}")
    public ResponseEntity<Void> deleteStop(@PathVariable Long trainId, @PathVariable Long stopId) {
        trainStopService.deleteStop(trainId, stopId);

        return ResponseEntity.noContent().build();
    }
}
