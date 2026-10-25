package com.trainconcierge.service;

import com.trainconcierge.dto.JourneyImpactResponse;
import com.trainconcierge.dto.PageResponse;
import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.DisruptionType;
import com.trainconcierge.entity.EventState;
import com.trainconcierge.entity.ImpactSeverity;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.JourneyLeg;
import com.trainconcierge.exception.InvalidRequestException;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.DisruptionEventRepository;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.JourneyLegRepository;
import com.trainconcierge.repository.JourneyRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic journey impact detection and lifecycle for a single journey.
 *
 * <p>Detection rules (no AI/ML, no live data):</p>
 * <ul>
 *   <li>leg train has an ongoing {@code CANCELLATION} → {@code CANCELLATION}
 *       impact (CRITICAL);</li>
 *   <li>leg train has an ongoing {@code SIGNIFICANT_DELAY} → compare the delayed
 *       expected arrival with the next leg's scheduled departure plus the
 *       planned transfer buffer. Delayed beyond the buffer → {@code
 *       MISSED_CONNECTION}; otherwise {@code DELAY};</li>
 *   <li>any cancellation, or any missed connection, also raises a journey-level
 *       {@code ITINERARY_DISRUPTED} impact (CRITICAL).</li>
 * </ul>
 *
 * <p>Missing schedule data (a {@code null} arrival or departure time) is never
 * guessed: the connection is then reported as {@code DELAY} and no missed
 * connection is claimed.</p>
 *
 * <p>Every mutation runs in one transaction per journey, and duplicate
 * prevention is enforced both by an application lookup and by the database
 * unique constraint on {@code active_key}.</p>
 */
@Service
public class JourneyImpactService {

    /** Both states mean the disruption is still ongoing. */
    private static final List<EventState> ONGOING_EVENT_STATES =
            List.of(EventState.ACTIVE, EventState.ACKNOWLEDGED);

    private static final int MINUTES_PER_DAY = 24 * 60;

    private final JourneyRepository journeyRepository;
    private final JourneyLegRepository journeyLegRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final JourneyImpactRepository journeyImpactRepository;

    public JourneyImpactService(JourneyRepository journeyRepository,
                                JourneyLegRepository journeyLegRepository,
                                DisruptionEventRepository disruptionEventRepository,
                                JourneyImpactRepository journeyImpactRepository) {
        this.journeyRepository = journeyRepository;
        this.journeyLegRepository = journeyLegRepository;
        this.disruptionEventRepository = disruptionEventRepository;
        this.journeyImpactRepository = journeyImpactRepository;
    }

    // ------------------------------------------------------------------
    // Analysis (one journey, one transaction)
    // ------------------------------------------------------------------

    /**
     * Recomputes the impacts of one journey from the currently ongoing
     * disruptions and applies them idempotently.
     */
    @Transactional
    public JourneyImpactOutcome reconcileJourney(Long journeyId) {
        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey not found with id: " + journeyId));
        return reconcile(journey);
    }

    private JourneyImpactOutcome reconcile(Journey journey) {
        LocalDate serviceDate = journey.getJourneyDate();
        List<JourneyLeg> legs = journeyLegRepository.findLegsWithDetails(journey.getId());

        Map<String, DesiredImpact> desired = new LinkedHashMap<>();
        for (int index = 0; index < legs.size(); index++) {
            JourneyLeg leg = legs.get(index);
            JourneyLeg nextLeg = index + 1 < legs.size() ? legs.get(index + 1) : null;

            List<DisruptionEvent> events = disruptionEventRepository
                    .findByTrainIdAndServiceDateAndEventStateInOrderByDetectedAtAscIdAsc(
                            leg.getTrain().getId(), serviceDate, ONGOING_EVENT_STATES);

            for (DisruptionEvent event : events) {
                collectDesired(desired, journey, leg, nextLeg, event);
            }
        }

        return applyDesired(journey, desired);
    }

    private void collectDesired(Map<String, DesiredImpact> desired,
                                Journey journey,
                                JourneyLeg leg,
                                JourneyLeg nextLeg,
                                DisruptionEvent event) {
        if (event.getDisruptionType() == DisruptionType.CANCELLATION) {
            desired.put(activeKey(journey.getId(), leg, event, ImpactType.CANCELLATION),
                    new DesiredImpact(activeKey(journey.getId(), leg, event, ImpactType.CANCELLATION),
                            leg, event, ImpactType.CANCELLATION, ImpactSeverity.CRITICAL, null,
                            cancellationExplanation(journey.getJourneyReference(), leg, event)));
            desired.put(activeKey(journey.getId(), null, event, ImpactType.ITINERARY_DISRUPTED),
                    new DesiredImpact(activeKey(journey.getId(), null, event, ImpactType.ITINERARY_DISRUPTED),
                            null, event, ImpactType.ITINERARY_DISRUPTED, ImpactSeverity.CRITICAL, null,
                            itineraryExplanation(journey.getJourneyReference(), leg, event, ImpactType.CANCELLATION)));
            return;
        }

        int delayMinutes = event.getDelayMinutes();
        Connection connection = evaluateConnection(leg, nextLeg, delayMinutes);

        if (connection.missed()) {
            desired.put(activeKey(journey.getId(), leg, event, ImpactType.MISSED_CONNECTION),
                    new DesiredImpact(activeKey(journey.getId(), leg, event, ImpactType.MISSED_CONNECTION),
                            leg, event, ImpactType.MISSED_CONNECTION, missedSeverity(connection, delayMinutes),
                            delayMinutes, missedConnectionExplanation(leg, nextLeg, connection, delayMinutes)));
            desired.put(activeKey(journey.getId(), null, event, ImpactType.ITINERARY_DISRUPTED),
                    new DesiredImpact(activeKey(journey.getId(), null, event, ImpactType.ITINERARY_DISRUPTED),
                            null, event, ImpactType.ITINERARY_DISRUPTED, ImpactSeverity.CRITICAL, null,
                            itineraryExplanation(journey.getJourneyReference(), leg, event,
                                    ImpactType.MISSED_CONNECTION)));
        } else {
            desired.put(activeKey(journey.getId(), leg, event, ImpactType.DELAY),
                    new DesiredImpact(activeKey(journey.getId(), leg, event, ImpactType.DELAY),
                            leg, event, ImpactType.DELAY, delaySeverity(delayMinutes), delayMinutes,
                            delayExplanation(leg, nextLeg, connection, delayMinutes)));
        }
    }

    /**
     * Applies the desired impacts: creates the missing ones, refreshes the ones
     * that changed, and resolves the active ones that are no longer warranted.
     */
    private JourneyImpactOutcome applyDesired(Journey journey, Map<String, DesiredImpact> desired) {
        List<JourneyImpact> active = journeyImpactRepository
                .findByJourneyIdAndImpactStateOrderByDetectedAtAscIdAsc(journey.getId(), ImpactState.ACTIVE);

        Map<String, JourneyImpact> activeByKey = new HashMap<>();
        for (JourneyImpact impact : active) {
            if (impact.getActiveKey() != null) {
                activeByKey.put(impact.getActiveKey(), impact);
            }
        }

        OffsetDateTime now = OffsetDateTime.now();
        int created = 0;
        int updated = 0;
        int resolved = 0;
        int unchanged = 0;

        for (DesiredImpact wanted : desired.values()) {
            JourneyImpact existing = activeByKey.get(wanted.activeKey());
            if (existing == null) {
                journeyImpactRepository.save(newImpact(journey, wanted, now));
                created++;
            } else if (refresh(existing, wanted, now)) {
                journeyImpactRepository.save(existing);
                updated++;
            } else {
                unchanged++;
            }
        }

        for (JourneyImpact impact : active) {
            boolean stillWanted = impact.getActiveKey() != null && desired.containsKey(impact.getActiveKey());
            if (!stillWanted) {
                resolve(impact, now);
                resolved++;
            }
        }

        return new JourneyImpactOutcome(created, updated, resolved, unchanged);
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public JourneyImpactResponse getImpact(Long id) {
        return journeyImpactRepository.findById(id)
                .map(JourneyImpactResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Journey impact not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<JourneyImpactResponse> search(Long journeyId,
                                                      Long disruptionEventId,
                                                      ImpactType impactType,
                                                      ImpactSeverity severity,
                                                      ImpactState impactState,
                                                      Integer minDelayMinutes,
                                                      Pageable pageable) {
        if (minDelayMinutes != null && minDelayMinutes < 0) {
            throw new InvalidRequestException("minDelayMinutes must not be negative");
        }

        Page<JourneyImpact> page = journeyImpactRepository.search(
                journeyId, disruptionEventId, impactType, severity, impactState, minDelayMinutes, pageable);
        return PageResponse.from(page, JourneyImpactResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<JourneyImpactResponse> listByJourney(Long journeyId, Pageable pageable) {
        if (!journeyRepository.existsById(journeyId)) {
            throw new ResourceNotFoundException("Journey not found with id: " + journeyId);
        }
        return search(journeyId, null, null, null, null, null, pageable);
    }

    // ------------------------------------------------------------------
    // Detection helpers
    // ------------------------------------------------------------------

    /**
     * Compares the delayed expected arrival of one leg with the scheduled
     * departure of the next leg. A single midnight crossing is handled by adding
     * one day to the later local time; the timetable model has no absolute dates,
     * so multi-day connections are out of scope and are documented as such.
     */
    private Connection evaluateConnection(JourneyLeg leg, JourneyLeg nextLeg, int delayMinutes) {
        if (nextLeg == null) {
            return Connection.finalLeg();
        }

        LocalTime scheduledArrival = leg.getArrivalTime();
        LocalTime scheduledDeparture = nextLeg.getDepartureTime();
        if (scheduledArrival == null || scheduledDeparture == null) {
            return Connection.unknownSchedule();
        }

        int arrivalMinutes = minutesOfDay(scheduledArrival);
        int departureMinutes = minutesOfDay(scheduledDeparture);
        if (departureMinutes < arrivalMinutes) {
            departureMinutes += MINUTES_PER_DAY;
        }

        int transferBufferMinutes = departureMinutes - arrivalMinutes;
        boolean missed = delayMinutes > transferBufferMinutes;
        return new Connection(true, true, scheduledArrival, scheduledDeparture,
                transferBufferMinutes, missed);
    }

    /** Overshoot = delay beyond the buffer; a larger overshoot is more severe. */
    private static ImpactSeverity missedSeverity(Connection connection, int delayMinutes) {
        int overshoot = delayMinutes - connection.transferBufferMinutes();
        if (overshoot >= 60) {
            return ImpactSeverity.CRITICAL;
        }
        if (overshoot >= 30) {
            return ImpactSeverity.HIGH;
        }
        return ImpactSeverity.MEDIUM;
    }

    private static ImpactSeverity delaySeverity(int delayMinutes) {
        if (delayMinutes >= 120) {
            return ImpactSeverity.HIGH;
        }
        if (delayMinutes >= 60) {
            return ImpactSeverity.MEDIUM;
        }
        return ImpactSeverity.LOW;
    }

    // ------------------------------------------------------------------
    // Explanations
    // ------------------------------------------------------------------

    private static String cancellationExplanation(String journeyReference, JourneyLeg leg, DisruptionEvent event) {
        return "Journey %s: leg %d (train %s, %s -> %s) is cancelled for service date %s, so the leg cannot run."
                .formatted(journeyReference, leg.getLegSequence(), leg.getTrain().getTrainNumber(),
                        leg.getSourceStation().getStationCode(), leg.getDestinationStation().getStationCode(),
                        event.getServiceDate());
    }

    private static String itineraryExplanation(String journeyReference,
                                               JourneyLeg leg,
                                               DisruptionEvent event,
                                               ImpactType cause) {
        String reason = cause == ImpactType.CANCELLATION
                ? "leg %d (train %s) is cancelled for service date %s".formatted(
                        leg.getLegSequence(), leg.getTrain().getTrainNumber(), event.getServiceDate())
                : "leg %d (train %s) misses its connection at %s".formatted(
                        leg.getLegSequence(), leg.getTrain().getTrainNumber(),
                        leg.getDestinationStation().getStationCode());
        return "Journey %s cannot be completed as planned: %s.".formatted(journeyReference, reason);
    }

    private static String missedConnectionExplanation(JourneyLeg leg,
                                                      JourneyLeg nextLeg,
                                                      Connection connection,
                                                      int delayMinutes) {
        int expectedArrivalMinutes = minutesOfDay(connection.scheduledArrival()) + delayMinutes;
        return ("Leg %d (train %s) is delayed %d min: expected arrival at %s is %s, but leg %d departs at %s "
                + "(planned transfer buffer %d min), so the connection is missed.")
                .formatted(leg.getLegSequence(), leg.getTrain().getTrainNumber(), delayMinutes,
                        leg.getDestinationStation().getStationCode(), formatMinutes(expectedArrivalMinutes),
                        nextLeg.getLegSequence(), connection.scheduledDeparture(),
                        connection.transferBufferMinutes());
    }

    private static String delayExplanation(JourneyLeg leg,
                                           JourneyLeg nextLeg,
                                           Connection connection,
                                           int delayMinutes) {
        if (nextLeg == null) {
            return "Leg %d (train %s) is delayed %d min; this is the final leg, so no connection is at risk."
                    .formatted(leg.getLegSequence(), leg.getTrain().getTrainNumber(), delayMinutes);
        }
        if (!connection.scheduleKnown()) {
            return ("Leg %d (train %s) is delayed %d min; the transfer buffer to leg %d cannot be computed because "
                    + "a scheduled arrival or departure time is missing, so a missed connection is not claimed.")
                    .formatted(leg.getLegSequence(), leg.getTrain().getTrainNumber(), delayMinutes,
                            nextLeg.getLegSequence());
        }
        return ("Leg %d (train %s) is delayed %d min; the planned transfer buffer of %d min at %s is still "
                + "sufficient, so the connection is not threatened.")
                .formatted(leg.getLegSequence(), leg.getTrain().getTrainNumber(), delayMinutes,
                        connection.transferBufferMinutes(), leg.getDestinationStation().getStationCode());
    }

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private static JourneyImpact newImpact(Journey journey, DesiredImpact wanted, OffsetDateTime now) {
        JourneyImpact impact = new JourneyImpact();
        impact.setJourney(journey);
        impact.setJourneyLeg(wanted.leg());
        impact.setDisruptionEvent(wanted.event());
        impact.setImpactType(wanted.impactType());
        impact.setSeverity(wanted.severity());
        impact.setDelayMinutes(wanted.delayMinutes());
        impact.setExplanation(wanted.explanation());
        impact.setDetectedAt(now);
        impact.setLastObservedAt(now);
        impact.setImpactState(ImpactState.ACTIVE);
        impact.setActiveKey(wanted.activeKey());
        return impact;
    }

    /** @return true if a meaningful field changed (the detection time never does) */
    private static boolean refresh(JourneyImpact impact, DesiredImpact wanted, OffsetDateTime now) {
        boolean changed = impact.getSeverity() != wanted.severity()
                || !Objects.equals(impact.getDelayMinutes(), wanted.delayMinutes())
                || !Objects.equals(impact.getExplanation(), wanted.explanation());

        if (changed) {
            impact.setSeverity(wanted.severity());
            impact.setDelayMinutes(wanted.delayMinutes());
            impact.setExplanation(wanted.explanation());
        }
        impact.setLastObservedAt(now);
        return changed;
    }

    private static void resolve(JourneyImpact impact, OffsetDateTime now) {
        impact.setImpactState(ImpactState.RESOLVED);
        impact.setResolvedAt(now);
        impact.setLastObservedAt(now);
        impact.setActiveKey(null);
    }

    private static int minutesOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static String formatMinutes(int totalMinutes) {
        int days = Math.floorDiv(totalMinutes, MINUTES_PER_DAY);
        int normalized = Math.floorMod(totalMinutes, MINUTES_PER_DAY);
        String suffix = days > 0 ? " (+%d day%s)".formatted(days, days == 1 ? "" : "s") : "";
        return "%02d:%02d%s".formatted(normalized / 60, normalized % 60, suffix);
    }

    private static String activeKey(Long journeyId, JourneyLeg leg, DisruptionEvent event, ImpactType type) {
        long legPart = leg == null ? 0L : leg.getId();
        return "J%d:L%d:E%d:%s".formatted(Objects.requireNonNull(journeyId),
                legPart, Objects.requireNonNull(event).getId(), type);
    }

    /** The scheduled timeline between a leg and the following leg. */
    private record Connection(boolean hasNextLeg,
                              boolean scheduleKnown,
                              LocalTime scheduledArrival,
                              LocalTime scheduledDeparture,
                              int transferBufferMinutes,
                              boolean missed) {

        static Connection finalLeg() {
            return new Connection(false, false, null, null, 0, false);
        }

        static Connection unknownSchedule() {
            return new Connection(true, false, null, null, 0, false);
        }
    }
}
