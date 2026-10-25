package com.trainconcierge.service;

import com.trainconcierge.config.TrainConciergeProperties;
import com.trainconcierge.dto.DisruptionEventResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.EventState;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.exception.InvalidRequestException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.DisruptionEventRepository;
import com.trainconcierge.repository.TrainRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the disruption-event lifecycle for a single train and service date.
 *
 * <p>Every mutation runs in one transaction, so a train's current status and its
 * events can never be left half-applied. Duplicate prevention is enforced twice:
 * an application-level lookup of the ongoing event, and the database unique
 * constraint on {@code active_key}.</p>
 */
@Service
public class DisruptionEventService {

    /** Both states mean the disruption is still ongoing and still holds its key. */
    private static final List<EventState> ONGOING_STATES = List.of(EventState.ACTIVE, EventState.ACKNOWLEDGED);

    private final DisruptionEventRepository disruptionEventRepository;
    private final TrainRepository trainRepository;
    private final TrainConciergeProperties properties;

    public DisruptionEventService(DisruptionEventRepository disruptionEventRepository,
                                  TrainRepository trainRepository,
                                  TrainConciergeProperties properties) {
        this.disruptionEventRepository = disruptionEventRepository;
        this.trainRepository = trainRepository;
        this.properties = properties;
    }

    // ------------------------------------------------------------------
    // Monitoring
    // ------------------------------------------------------------------

    /**
     * Applies one observation of a train's current status to its disruption events.
     *
     * <p>May throw {@code DataIntegrityViolationException} if another monitoring
     * run created the same ongoing event concurrently; the caller then falls back
     * to {@link #refreshConcurrentEvent}.</p>
     */
    @Transactional
    public ObservationResult applyObservation(Long trainId,
                                              LocalDate serviceDate,
                                              TrainRunningStatus observedStatus,
                                              int delayMinutes,
                                              String reason) {
        List<DisruptionEvent> ongoing = findOngoing(trainId, serviceDate);
        DisruptionType desired = detectDisruptionType(observedStatus, delayMinutes);
        OffsetDateTime now = OffsetDateTime.now();

        if (desired == null) {
            // ON_TIME, or a delay below the threshold: no active disruption.
            ongoing.forEach(event -> resolve(event, now));
            return ongoing.isEmpty() ? ObservationResult.none()
                    : new ObservationResult(0, 0, ongoing.size());
        }

        DisruptionEvent matching = null;
        int resolved = 0;
        for (DisruptionEvent event : ongoing) {
            if (event.getDisruptionType() == desired && matching == null) {
                matching = event;
            } else {
                // Incompatible (e.g. an active delay while the train is now cancelled).
                resolve(event, now);
                resolved++;
            }
        }

        if (matching != null) {
            refresh(matching, observedStatus, delayMinutes, reason, now);
            return new ObservationResult(0, 1, resolved);
        }

        DisruptionEvent created = new DisruptionEvent();
        created.setTrain(trainRepository.getReferenceById(trainId));
        created.setServiceDate(serviceDate);
        created.setDisruptionType(desired);
        created.setObservedTrainStatus(observedStatus);
        created.setDelayMinutes(delayMinutes);
        created.setReason(reason);
        created.setDetectedAt(now);
        created.setLastObservedAt(now);
        created.setEventState(EventState.ACTIVE);
        created.setActiveKey(activeKey(trainId, serviceDate, desired));
        disruptionEventRepository.save(created);
        return new ObservationResult(1, 0, resolved);
    }

    /**
     * Fallback used when {@link #applyObservation} lost a race and the database
     * unique constraint rejected a duplicate ongoing event: the already-committed
     * event is simply refreshed instead.
     */
    @Transactional
    public ObservationResult refreshConcurrentEvent(Long trainId,
                                                    LocalDate serviceDate,
                                                    TrainRunningStatus observedStatus,
                                                    int delayMinutes,
                                                    String reason) {
        DisruptionType desired = detectDisruptionType(observedStatus, delayMinutes);
        if (desired == null) {
            return ObservationResult.none();
        }

        DisruptionEvent existing = disruptionEventRepository.findByActiveKey(activeKey(trainId, serviceDate, desired))
                .orElseThrow(() -> new IllegalStateException(
                        "Concurrent disruption event disappeared for train %d on %s (%s)"
                                .formatted(trainId, serviceDate, desired)));
        refresh(existing, observedStatus, delayMinutes, reason, OffsetDateTime.now());
        return ObservationResult.ofUpdated();
    }

    // ------------------------------------------------------------------
    // Queries and acknowledgement
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public DisruptionEventResponse getEvent(Long id) {
        return DisruptionEventResponse.from(findEvent(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<DisruptionEventResponse> search(Long trainId,
                                                       LocalDate serviceDate,
                                                       DisruptionType type,
                                                       EventState state,
                                                       Integer minDelayMinutes,
                                                       Pageable pageable) {
        if (minDelayMinutes != null && minDelayMinutes < 0) {
            throw new InvalidRequestException("minDelayMinutes must not be negative");
        }

        Page<DisruptionEvent> page =
                disruptionEventRepository.search(trainId, serviceDate, type, state, minDelayMinutes, pageable);
        return PageResponse.from(page, DisruptionEventResponse::from);
    }

    @Transactional
    public DisruptionEventResponse acknowledge(Long id) {
        DisruptionEvent event = findEvent(id);

        if (event.getEventState() == EventState.RESOLVED) {
            throw new InvalidRequestException("A resolved disruption event cannot be acknowledged");
        }
        if (event.getEventState() == EventState.ACTIVE) {
            event.setEventState(EventState.ACKNOWLEDGED);
            disruptionEventRepository.save(event);
        }
        return DisruptionEventResponse.from(event);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private DisruptionEvent findEvent(Long id) {
        return disruptionEventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Disruption event not found with id: " + id));
    }

    private List<DisruptionEvent> findOngoing(Long trainId, LocalDate serviceDate) {
        return disruptionEventRepository.findByTrainIdAndServiceDateAndEventStateInOrderByDetectedAtAscIdAsc(
                trainId, serviceDate, ONGOING_STATES);
    }

    /**
     * CANCELLED is always a cancellation. DELAYED is a significant delay only when
     * it reaches the configured threshold, otherwise it is not an active
     * disruption. ON_TIME is never a disruption. Missing status data is handled by
     * the monitoring service and is never interpreted as on time.
     */
    private DisruptionType detectDisruptionType(TrainRunningStatus status, int delayMinutes) {
        if (status == TrainRunningStatus.CANCELLED) {
            return DisruptionType.CANCELLATION;
        }
        if (status == TrainRunningStatus.DELAYED && delayMinutes >= thresholdMinutes()) {
            return DisruptionType.SIGNIFICANT_DELAY;
        }
        return null;
    }

    private void refresh(DisruptionEvent event,
                         TrainRunningStatus observedStatus,
                         int delayMinutes,
                         String reason,
                         OffsetDateTime now) {
        event.setObservedTrainStatus(observedStatus);
        event.setDelayMinutes(delayMinutes);
        event.setReason(reason);
        event.setLastObservedAt(now);
        disruptionEventRepository.save(event);
    }

    private void resolve(DisruptionEvent event, OffsetDateTime now) {
        event.setEventState(EventState.RESOLVED);
        event.setResolvedAt(now);
        event.setLastObservedAt(now);
        event.setActiveKey(null);
        disruptionEventRepository.save(event);
    }

    private int thresholdMinutes() {
        return properties.getDisruption().getDelayThresholdMinutes();
    }

    private static String activeKey(Long trainId, LocalDate serviceDate, DisruptionType type) {
        return "T%s:%s:%s".formatted(Objects.requireNonNull(trainId), serviceDate, type);
    }
}
