package com.trainconcierge.service;

import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.dto.TrainStatusActionRequest;
import com.trainconcierge.dto.TrainStatusHistoryResponse;
import com.trainconcierge.dto.TrainStatusRequest;
import com.trainconcierge.dto.TrainStatusResponse;
import com.trainconcierge.dto.TrainStatusSimulationRequest;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.StatusSource;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStatus;
import com.trainconcierge.entity.TrainStatusHistory;
import com.trainconcierge.exception.InvalidRequestException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStatusHistoryRepository;
import com.trainconcierge.repository.TrainStatusRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulated train running-status management.
 *
 * <p>All writes go through {@link #upsert}, which persists the current status and
 * its history entry in a single transaction so the two can never diverge. These
 * statuses are demonstration data only and are not connected to any live railway
 * system.</p>
 */
@Service
public class TrainStatusService {

    private final TrainRepository trainRepository;
    private final StationRepository stationRepository;
    private final TrainStatusRepository trainStatusRepository;
    private final TrainStatusHistoryRepository trainStatusHistoryRepository;

    public TrainStatusService(TrainRepository trainRepository,
                              StationRepository stationRepository,
                              TrainStatusRepository trainStatusRepository,
                              TrainStatusHistoryRepository trainStatusHistoryRepository) {
        this.trainRepository = trainRepository;
        this.stationRepository = stationRepository;
        this.trainStatusRepository = trainStatusRepository;
        this.trainStatusHistoryRepository = trainStatusHistoryRepository;
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TrainStatusResponse getStatus(Long trainId, LocalDate serviceDate) {
        return trainStatusRepository.findByTrainIdAndServiceDate(trainId, serviceDate)
                .map(TrainStatusResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No running status reported for train %d on %s".formatted(trainId, serviceDate)));
    }

    @Transactional(readOnly = true)
    public PageResponse<TrainStatusResponse> search(Long trainId, LocalDate serviceDate,
                                                    TrainRunningStatus status, Integer minDelayMinutes,
                                                    Pageable pageable) {
        if (minDelayMinutes != null && minDelayMinutes < 0) {
            throw new InvalidRequestException("minDelayMinutes must not be negative");
        }

        Page<TrainStatus> page = trainStatusRepository.search(trainId, serviceDate, status, minDelayMinutes, pageable);
        return PageResponse.from(page, TrainStatusResponse::from);
    }

    @Transactional(readOnly = true)
    public List<TrainStatusHistoryResponse> getHistory(Long trainId, LocalDate serviceDate) {
        return trainStatusHistoryRepository
                .findByTrainIdAndServiceDateOrderByChangedAtAscIdAsc(trainId, serviceDate)
                .stream()
                .map(TrainStatusHistoryResponse::from)
                .toList();
    }

    // ------------------------------------------------------------------
    // Writes (simulation / administrative operations)
    // ------------------------------------------------------------------

    @Transactional
    public StatusUpsertResult upsert(Long trainId, TrainStatusRequest request) {
        return upsert(trainId,
                request.serviceDate(),
                request.status(),
                request.delayMinutes(),
                request.currentStationId(),
                request.reason(),
                request.expectedDepartureTime(),
                request.expectedArrivalTime(),
                StatusSource.SIMULATION);
    }

    @Transactional
    public TrainStatusResponse setOnTime(Long trainId, TrainStatusActionRequest request) {
        return upsert(trainId, request.serviceDate(), TrainRunningStatus.ON_TIME, 0,
                request.currentStationId(), request.reason(), null, null, StatusSource.SIMULATION).status();
    }

    @Transactional
    public TrainStatusResponse setDelayed(Long trainId, TrainStatusActionRequest request) {
        if (request.delayMinutes() == null || request.delayMinutes() <= 0) {
            throw new InvalidRequestException("A delay operation requires a positive delayMinutes value");
        }
        return upsert(trainId, request.serviceDate(), TrainRunningStatus.DELAYED, request.delayMinutes(),
                request.currentStationId(), request.reason(), null, null, StatusSource.SIMULATION).status();
    }

    @Transactional
    public TrainStatusResponse cancel(Long trainId, TrainStatusActionRequest request) {
        return upsert(trainId, request.serviceDate(), TrainRunningStatus.CANCELLED, 0,
                request.currentStationId(), request.reason(), null, null, StatusSource.SIMULATION).status();
    }

    @Transactional
    public TrainStatusResponse restore(Long trainId, TrainStatusActionRequest request) {
        TrainStatus current = trainStatusRepository.findByTrainIdAndServiceDate(trainId, request.serviceDate())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No running status reported for train %d on %s, nothing to restore"
                                .formatted(trainId, request.serviceDate())));

        if (current.getStatus() == TrainRunningStatus.ON_TIME) {
            throw new InvalidRequestException("Train is already ON_TIME for the given service date");
        }

        return upsert(trainId, request.serviceDate(), TrainRunningStatus.ON_TIME, 0,
                request.currentStationId(), request.reason(), null, null, StatusSource.SIMULATION).status();
    }

    @Transactional
    public StatusUpsertResult simulate(TrainStatusSimulationRequest request) {
        return upsert(request.trainId(), request.serviceDate(), request.status(), request.delayMinutes(),
                request.currentStationId(), request.reason(), null, null, StatusSource.SIMULATION);
    }

    // ------------------------------------------------------------------
    // Core logic
    // ------------------------------------------------------------------

    private StatusUpsertResult upsert(Long trainId,
                                      LocalDate serviceDate,
                                      TrainRunningStatus status,
                                      Integer delayMinutes,
                                      Long currentStationId,
                                      String reason,
                                      LocalTime expectedDepartureTime,
                                      LocalTime expectedArrivalTime,
                                      StatusSource source) {
        if (serviceDate == null) {
            throw new InvalidRequestException("serviceDate must not be null");
        }
        if (status == null) {
            throw new InvalidRequestException("status must not be null");
        }

        Train train = trainRepository.findById(trainId)
                .orElseThrow(() -> new ResourceNotFoundException("Train not found with id: " + trainId));
        if (!train.isActive()) {
            throw new InvalidRequestException("Train " + train.getTrainNumber() + " is not active");
        }

        int delay = normalizeDelay(status, delayMinutes);
        String normalizedReason = trimToNull(reason);
        Station station = resolveStation(currentStationId);

        TrainStatus current = trainStatusRepository.findByTrainIdAndServiceDate(trainId, serviceDate).orElse(null);
        boolean created = current == null;

        if (!created && !hasMeaningfulChange(current, status, delay, station, normalizedReason,
                expectedDepartureTime, expectedArrivalTime)) {
            // Nothing changed: do not append a duplicate history row.
            return new StatusUpsertResult(TrainStatusResponse.from(current), false);
        }

        TrainRunningStatus previousStatus = created ? null : current.getStatus();
        Integer previousDelay = created ? null : current.getDelayMinutes();

        if (created) {
            current = new TrainStatus();
            current.setTrain(train);
            current.setServiceDate(serviceDate);
        }

        OffsetDateTime now = OffsetDateTime.now();
        current.setStatus(status);
        current.setDelayMinutes(delay);
        current.setCurrentStation(station);
        current.setReason(normalizedReason);
        current.setExpectedDepartureTime(expectedDepartureTime);
        current.setExpectedArrivalTime(expectedArrivalTime);
        current.setLastUpdatedAt(now);
        TrainStatus saved = trainStatusRepository.save(current);

        TrainStatusHistory history = new TrainStatusHistory();
        history.setTrain(train);
        history.setServiceDate(serviceDate);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(status);
        history.setPreviousDelayMinutes(previousDelay);
        history.setNewDelayMinutes(delay);
        history.setReason(normalizedReason);
        history.setCurrentStation(station);
        history.setSource(source);
        history.setChangedAt(now);
        trainStatusHistoryRepository.save(history);

        return new StatusUpsertResult(TrainStatusResponse.from(saved), created);
    }

    /**
     * Validates the delay against the target status and returns the value to
     * store. A cancellation is never expressed as a delay, so it always stores 0.
     */
    private int normalizeDelay(TrainRunningStatus status, Integer delayMinutes) {
        int delay = delayMinutes == null ? 0 : delayMinutes;

        if (delay < 0) {
            throw new InvalidRequestException("delayMinutes must not be negative");
        }

        return switch (status) {
            case ON_TIME -> {
                if (delay != 0) {
                    throw new InvalidRequestException("An ON_TIME status must have a zero delay");
                }
                yield 0;
            }
            case DELAYED -> {
                if (delay <= 0) {
                    throw new InvalidRequestException("A DELAYED status must have a positive delay");
                }
                yield delay;
            }
            case CANCELLED -> 0;
        };
    }

    private Station resolveStation(Long currentStationId) {
        if (currentStationId == null) {
            return null;
        }
        return stationRepository.findById(currentStationId)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found with id: " + currentStationId));
    }

    private boolean hasMeaningfulChange(TrainStatus current,
                                        TrainRunningStatus status,
                                        int delay,
                                        Station station,
                                        String reason,
                                        LocalTime expectedDepartureTime,
                                        LocalTime expectedArrivalTime) {
        return current.getStatus() != status
                || current.getDelayMinutes() != delay
                || !Objects.equals(stationId(current.getCurrentStation()), stationId(station))
                || !Objects.equals(current.getReason(), reason)
                || !Objects.equals(current.getExpectedDepartureTime(), expectedDepartureTime)
                || !Objects.equals(current.getExpectedArrivalTime(), expectedArrivalTime);
    }

    private static Long stationId(Station station) {
        return station == null ? null : station.getId();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
