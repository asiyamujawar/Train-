package com.trainconcierge.controller;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.TrainStatusActionRequest;
import com.trainconcierge.dto.TrainStatusHistoryResponse;
import com.trainconcierge.dto.TrainStatusRequest;
import com.trainconcierge.dto.TrainStatusResponse;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.service.StatusUpsertResult;
import com.trainconcierge.service.TrainStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read endpoints plus the status-mutation endpoints.
 *
 * <p>The mutation endpoints ({@code PUT}, and the {@code POST .../on-time},
 * {@code .../delay}, {@code .../cancel}, {@code .../restore} operations) are
 * <strong>simulation / administrative</strong> operations for the college
 * demonstration. They are not connected to any live railway system. Timestamps
 * are always generated on the server and history records are never accepted
 * directly from clients. Role-based access control is planned for a later
 * phase.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Train Running Status (simulation)",
        description = "Simulated train running status and status history. Demonstration data only.")
public class TrainStatusController {

    private final TrainStatusService trainStatusService;

    public TrainStatusController(TrainStatusService trainStatusService) {
        this.trainStatusService = trainStatusService;
    }

    @Operation(summary = "Get the current simulated status of a train for a service date")
    @GetMapping("/trains/{trainId}/status")
    public TrainStatusResponse getStatus(@PathVariable Long trainId,
                                         @RequestParam LocalDate serviceDate) {
        return trainStatusService.getStatus(trainId, serviceDate);
    }

    @Operation(summary = "List train statuses with optional filters (paginated)")
    @GetMapping("/train-statuses")
    public PageResponse<TrainStatusResponse> search(
            @RequestParam(required = false) Long trainId,
            @RequestParam(required = false) LocalDate serviceDate,
            @RequestParam(required = false) TrainRunningStatus status,
            @RequestParam(required = false) Integer minDelayMinutes,
            @PageableDefault(size = 20, sort = {"serviceDate", "id"}, direction = Sort.Direction.DESC)
            Pageable pageable) {
        return trainStatusService.search(trainId, serviceDate, status, minDelayMinutes, pageable);
    }

    @Operation(summary = "Get the status history of a train for a service date (oldest first)")
    @GetMapping("/trains/{trainId}/status-history")
    public List<TrainStatusHistoryResponse> getHistory(@PathVariable Long trainId,
                                                       @RequestParam LocalDate serviceDate) {
        return trainStatusService.getHistory(trainId, serviceDate);
    }

    @Operation(summary = "SIMULATION: create or update the current status of a train")
    @PutMapping("/trains/{trainId}/status")
    public ResponseEntity<TrainStatusResponse> upsert(@PathVariable Long trainId,
                                                       @Valid @RequestBody TrainStatusRequest request) {
        StatusUpsertResult result = trainStatusService.upsert(trainId, request);
        if (result.created()) {
            return ResponseEntity
                    .created(URI.create("/api/v1/trains/" + trainId + "/status?serviceDate="
                            + result.status().serviceDate()))
                    .body(result.status());
        }
        return ResponseEntity.ok(result.status());
    }

    @Operation(summary = "SIMULATION: set a train ON_TIME for a service date")
    @PostMapping("/trains/{trainId}/status/on-time")
    public TrainStatusResponse onTime(@PathVariable Long trainId,
                                      @Valid @RequestBody TrainStatusActionRequest request) {
        return trainStatusService.setOnTime(trainId, request);
    }

    @Operation(summary = "SIMULATION: set a train DELAYED by a positive number of minutes")
    @PostMapping("/trains/{trainId}/status/delay")
    public TrainStatusResponse delay(@PathVariable Long trainId,
                                     @Valid @RequestBody TrainStatusActionRequest request) {
        return trainStatusService.setDelayed(trainId, request);
    }

    @Operation(summary = "SIMULATION: cancel a train for a service date")
    @PostMapping("/trains/{trainId}/status/cancel")
    public TrainStatusResponse cancel(@PathVariable Long trainId,
                                      @Valid @RequestBody TrainStatusActionRequest request) {
        return trainStatusService.cancel(trainId, request);
    }

    @Operation(summary = "SIMULATION: restore a cancelled/delayed train to ON_TIME")
    @PostMapping("/trains/{trainId}/status/restore")
    public TrainStatusResponse restore(@PathVariable Long trainId,
                                       @Valid @RequestBody TrainStatusActionRequest request) {
        return trainStatusService.restore(trainId, request);
    }
}
