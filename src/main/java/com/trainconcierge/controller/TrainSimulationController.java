package com.trainconcierge.controller;

import com.trainconcierge.dto.TrainStatusResponse;
import com.trainconcierge.dto.TrainStatusSimulationRequest;
import com.trainconcierge.service.StatusUpsertResult;
import com.trainconcierge.service.TrainStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Convenience endpoint for the demonstration. It carries the train id in the body
 * so a demo script can drive statuses from a single URL, but it delegates to the
 * same service logic as {@link TrainStatusController} and adds no separate rules.
 */
@RestController
@RequestMapping("/api/v1/simulations")
@Tag(name = "Simulations", description = "Demonstration convenience endpoints. Simulated data only.")
public class TrainSimulationController {

    private final TrainStatusService trainStatusService;

    public TrainSimulationController(TrainStatusService trainStatusService) {
        this.trainStatusService = trainStatusService;
    }

    @Operation(summary = "SIMULATION: create or update a train status from a single request")
    @PostMapping("/train-status")
    public ResponseEntity<TrainStatusResponse> simulate(
            @Valid @RequestBody TrainStatusSimulationRequest request) {
        StatusUpsertResult result = trainStatusService.simulate(request);
        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/v1/trains/" + request.trainId() + "/status?serviceDate="
                            + request.serviceDate()))
                    .body(result.status());
        }
        return ResponseEntity.ok(result.status());
    }
}
