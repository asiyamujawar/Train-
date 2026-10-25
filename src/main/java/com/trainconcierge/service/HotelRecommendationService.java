package com.trainconcierge.service;

import com.trainconcierge.config.TrainConciergeProperties;
import com.trainconcierge.dto.HotelRecommendationItem;
import com.trainconcierge.dto.HotelResponse;
import com.trainconcierge.entity.Hotel;
import com.trainconcierge.entity.HotelDecisionOutcome;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.JourneyLeg;
import com.trainconcierge.entity.HotelRecommendation;
import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.entity.Station;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.HotelRecommendationRepository;
import com.trainconcierge.repository.HotelRepository;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.JourneyLegRepository;
import com.trainconcierge.repository.JourneyRepository;
import com.trainconcierge.repository.TrainRecommendationRepository;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides whether a disrupted journey is likely to need an overnight hotel stay
 * and, when it does, ranks suitable hotels near the station where the passenger
 * is stranded.
 *
 * <p>The whole module is deterministic plain Java over Phases 1-6 data: no ML,
 * no external APIs, and <strong>no booking</strong>. A recommendation never
 * claims that a room is available.</p>
 *
 * <h2>Decision rules (documented, independently testable)</h2>
 * <ol>
 *   <li>Only {@code ACTIVE} journey impacts are considered.</li>
 *   <li>No active impact at all → {@code SAME_DAY_TRAVEL_FEASIBLE}.</li>
 *   <li>Active impacts that are all journey-level (no leg) →
 *       {@code INSUFFICIENT_TRAVEL_DATA}, because the affected station is unknown.</li>
 *   <li>A <b>CANCELLATION</b> or <b>MISSED_CONNECTION</b> forces an overnight
 *       <em>unless</em> Phase 6 has an {@code AVAILABLE} alternative-train
 *       recommendation for the same impact (a feasible same-day alternative
 *       exists, so no hotel is needed).</li>
 *   <li>A plain <b>DELAY</b> forces an overnight only when the delayed arrival
 *       crosses midnight <em>or</em> the delay reaches
 *       {@code trainconcierge.hotel.overnight-delay-threshold-minutes}.</li>
 *   <li>If no impact forces an overnight but at least one could not be evaluated
 *       (missing timetable/impact times) → {@code INSUFFICIENT_TRAVEL_DATA};
 *       otherwise {@code SAME_DAY_TRAVEL_FEASIBLE}.</li>
 * </ol>
 *
 * <p>The "stranded station" is the source station of a cancelled leg (the
 * passenger never departed) and the destination station of a missed connection
 * or a forcing delay.</p>
 */
@Service
public class HotelRecommendationService {

    private static final int MINUTES_PER_DAY = 1440;
    private static final int HOTEL_POOL_LIMIT = 200;

    private static final Comparator<LegAssessment> FORCING_ORDER = Comparator
            .comparing((LegAssessment a) -> a.leg().getLegSequence(),
                    Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing((LegAssessment a) -> a.impact().getSeverity().ordinal(),
                    Comparator.reverseOrder())
            .thenComparing((LegAssessment a) -> a.impact().getId(),
                    Comparator.nullsLast(Comparator.naturalOrder()));

    private final JourneyRepository journeyRepository;
    private final JourneyLegRepository journeyLegRepository;
    private final JourneyImpactRepository journeyImpactRepository;
    private final TrainRecommendationRepository trainRecommendationRepository;
    private final HotelRepository hotelRepository;
    private final HotelRecommendationRepository hotelRecommendationRepository;
    private final HotelScoringService hotelScoringService;
    private final TrainConciergeProperties properties;

    public HotelRecommendationService(JourneyRepository journeyRepository,
                                      JourneyLegRepository journeyLegRepository,
                                      JourneyImpactRepository journeyImpactRepository,
                                      TrainRecommendationRepository trainRecommendationRepository,
                                      HotelRepository hotelRepository,
                                      HotelRecommendationRepository hotelRecommendationRepository,
                                      HotelScoringService hotelScoringService,
                                      TrainConciergeProperties properties) {
        this.journeyRepository = journeyRepository;
        this.journeyLegRepository = journeyLegRepository;
        this.journeyImpactRepository = journeyImpactRepository;
        this.trainRecommendationRepository = trainRecommendationRepository;
        this.hotelRepository = hotelRepository;
        this.hotelRecommendationRepository = hotelRecommendationRepository;
        this.hotelScoringService = hotelScoringService;
        this.properties = properties;
    }

    /** Evaluates a journey without persisting anything (used by the GET endpoint). */
    @Transactional(readOnly = true)
    public HotelRecommendationResult evaluate(Long journeyId, HotelScoringService.HotelFilters filters) {
        HotelScoringService.HotelFilters effectiveFilters =
                filters == null ? HotelScoringService.HotelFilters.none() : filters;

        Journey journey = journeyRepository.findById(journeyId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey not found with id: " + journeyId));

        List<JourneyImpact> activeImpacts = journeyImpactRepository
                .findByJourneyIdAndImpactStateOrderByDetectedAtAscIdAsc(journeyId, ImpactState.ACTIVE);

        if (activeImpacts.isEmpty()) {
            return new HotelRecommendationResult(journey, HotelDecisionOutcome.SAME_DAY_TRAVEL_FEASIBLE, null, null,
                    "No active journey impact requires an overnight stay.", List.of());
        }

        Map<Long, JourneyLeg> legsById = journeyLegRepository.findLegsWithDetails(journeyId).stream()
                .collect(Collectors.toMap(JourneyLeg::getId, Function.identity()));

        List<LegAssessment> assessments = new ArrayList<>();
        for (JourneyImpact impact : activeImpacts) {
            JourneyLeg legRef = impact.getJourneyLeg();
            if (legRef == null) {
                continue;
            }
            assessments.add(assessLeg(impact, legsById.get(legRef.getId())));
        }

        if (assessments.isEmpty()) {
            return new HotelRecommendationResult(journey, HotelDecisionOutcome.INSUFFICIENT_TRAVEL_DATA, null, null,
                    "The active disruption is recorded at journey level only, so the affected leg and station "
                            + "cannot be determined.", List.of());
        }

        List<LegAssessment> forcing = assessments.stream()
                .filter(assessment -> assessment.determinable() && assessment.forcesOvernight())
                .toList();

        if (!forcing.isEmpty()) {
            LegAssessment chosen = forcing.stream().min(FORCING_ORDER).orElseThrow();
            Station stranded = chosen.strandedStation();
            List<HotelRecommendationItem> recommendations = rankHotels(stranded, effectiveFilters);
            return new HotelRecommendationResult(journey, HotelDecisionOutcome.OVERNIGHT_STAY_LIKELY, stranded,
                    chosen.impact().getId(), overnightReason(chosen, stranded), recommendations);
        }

        boolean anyUndeterminable = assessments.stream().anyMatch(assessment -> !assessment.determinable());
        if (anyUndeterminable) {
            return new HotelRecommendationResult(journey, HotelDecisionOutcome.INSUFFICIENT_TRAVEL_DATA, null, null,
                    "The stored timetable or impact data is not sufficient to decide whether an overnight stay "
                            + "is needed.", List.of());
        }

        return new HotelRecommendationResult(journey, HotelDecisionOutcome.SAME_DAY_TRAVEL_FEASIBLE, null, null,
                "A same-day continuation of the journey remains possible, so no overnight stay is likely.",
                List.of());
    }

    /**
     * Evaluates a journey and persists the result with a safe refresh strategy:
     * suggestions are created, refreshed or expired - never deleted - and the
     * {@code active_key} keeps the current result set duplicate-free while
     * preserving history.
     */
    @Transactional
    public HotelRecommendationRunResult runForJourney(Long journeyId, HotelScoringService.HotelFilters filters) {
        HotelRecommendationResult evaluation = evaluate(journeyId, filters);
        Journey journey = evaluation.journey();
        Long relevantStationId = evaluation.relevantStation() == null ? null : evaluation.relevantStation().getId();

        List<HotelRecommendation> existing = hotelRecommendationRepository
                .findForJourney(journey.getId(), RecommendationStatus.AVAILABLE);

        if (evaluation.outcome() != HotelDecisionOutcome.OVERNIGHT_STAY_LIKELY
                || evaluation.recommendations().isEmpty()) {
            int expired = 0;
            for (HotelRecommendation row : existing) {
                expire(row);
                expired++;
            }
            String reason = evaluation.outcome() == HotelDecisionOutcome.OVERNIGHT_STAY_LIKELY
                    ? "An overnight stay is likely, but no hotel matched the affected station and the applied filters."
                    : evaluation.reason();
            return new HotelRecommendationRunResult(evaluation.outcome(), relevantStationId,
                    0, 0, expired, 0, 0, reason);
        }

        Map<Long, HotelRecommendation> activeByHotel = new HashMap<>();
        for (HotelRecommendation row : existing) {
            activeByHotel.put(row.getHotel().getId(), row);
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        int expired = 0;
        Set<Long> desiredHotelIds = new HashSet<>();

        for (HotelRecommendationItem item : evaluation.recommendations()) {
            Long hotelId = item.hotel().id();
            desiredHotelIds.add(hotelId);
            String activeKey = activeKey(journey.getId(), hotelId);
            Hotel hotel = hotelRepository.getReferenceById(hotelId);

            HotelRecommendation row = activeByHotel.get(hotelId);
            if (row == null) {
                HotelRecommendation newRow = new HotelRecommendation();
                apply(newRow, journey, evaluation, item, hotel, activeKey);
                hotelRecommendationRepository.save(newRow);
                created++;
            } else if (needsRefresh(row, evaluation, item)) {
                apply(row, journey, evaluation, item, hotel, activeKey);
                hotelRecommendationRepository.save(row);
                updated++;
            } else {
                unchanged++;
            }
        }

        for (HotelRecommendation row : existing) {
            if (!desiredHotelIds.contains(row.getHotel().getId())) {
                expire(row);
                expired++;
            }
        }

        return new HotelRecommendationRunResult(evaluation.outcome(), relevantStationId,
                created, updated, expired, unchanged, evaluation.recommendations().size(), evaluation.reason());
    }

    private LegAssessment assessLeg(JourneyImpact impact, JourneyLeg leg) {
        if (leg == null) {
            return LegAssessment.undeterminable();
        }
        return switch (impact.getImpactType()) {
            case CANCELLATION -> new LegAssessment(true, !hasFeasibleAlternative(impact.getId()),
                    leg.getSourceStation(), leg, null, impact);
            case MISSED_CONNECTION -> new LegAssessment(true, !hasFeasibleAlternative(impact.getId()),
                    leg.getDestinationStation(), leg, null, impact);
            case DELAY -> assessDelay(impact, leg);
            case ITINERARY_DISRUPTED -> LegAssessment.undeterminable();
        };
    }

    private LegAssessment assessDelay(JourneyImpact impact, JourneyLeg leg) {
        Integer delay = impact.getDelayMinutes();
        LocalTime arrival = leg.getArrivalTime();
        if (delay == null || arrival == null) {
            return LegAssessment.undeterminable();
        }
        int arrivalMinutes = arrival.getHour() * 60 + arrival.getMinute() + delay;
        boolean forcesOvernight = arrivalMinutes >= MINUTES_PER_DAY
                || delay >= properties.getHotel().getOvernightDelayThresholdMinutes();

        return new LegAssessment(true, forcesOvernight, leg.getDestinationStation(), leg, delay, impact);
    }

    private boolean hasFeasibleAlternative(Long impactId) {
        return !trainRecommendationRepository
                .findActiveForImpact(impactId, RecommendationStatus.AVAILABLE)
                .isEmpty();
    }

    private List<HotelRecommendationItem> rankHotels(Station station, HotelScoringService.HotelFilters filters) {
        List<Hotel> pool = hotelRepository.search(
                null,
                station.getId(),
                filters.currency(),
                Boolean.TRUE,
                filters.maxPrice(),
                filters.minRating(),
                filters.maxDistanceKm(),
                filters.amenity(),
                PageRequest.of(0, HOTEL_POOL_LIMIT, Sort.by(Sort.Direction.ASC, "id")))
                .getContent();

        List<Hotel> ranked = pool.stream().sorted(hotelScoringService.ranking()).toList();
        int limit = Math.max(0, properties.getHotel().getMaxRecommendations());

        List<HotelRecommendationItem> items = new ArrayList<>();
        for (Hotel hotel : ranked) {
            if (items.size() >= limit) {
                break;
            }
            items.add(new HotelRecommendationItem(items.size() + 1, HotelResponse.from(hotel),
                    hotelScoringService.explain(hotel, station, filters)));
        }
        return items;
    }

    private String overnightReason(LegAssessment chosen, Station stranded) {
        JourneyLeg leg = chosen.leg();
        String action = switch (chosen.impact().getImpactType()) {
            case CANCELLATION -> "is cancelled for the service date";
            case MISSED_CONNECTION -> "misses the onward connection";
            case DELAY -> "is delayed by " + chosen.delayMinutes()
                    + " minutes, making same-day onward travel impractical";
            default -> "disrupts the itinerary";
        };
        String reason = "Train " + leg.getTrain().getTrainNumber()
                + " on leg " + leg.getLegSequence()
                + " (" + leg.getSourceStation().getStationCode() + " -> "
                + leg.getDestinationStation().getStationCode() + ") " + action
                + " and no feasible same-day alternative was found, so the passenger is likely stranded at "
                + stranded.getName() + " (" + stranded.getStationCode() + ").";

        return HotelScoringService.truncate(reason);
    }

    private boolean needsRefresh(HotelRecommendation row, HotelRecommendationResult evaluation,
                                 HotelRecommendationItem item) {
        Long stationId = evaluation.relevantStation() == null ? null : evaluation.relevantStation().getId();
        return row.getRankPosition() != item.rank()
                || !Objects.equals(row.getReason(), item.reason())
                || row.getDecisionOutcome() != evaluation.outcome()
                || !Objects.equals(idOf(row.getJourneyImpact()), evaluation.forcingImpactId())
                || !Objects.equals(idOf(row.getRelevantStation()), stationId);
    }

    private void apply(HotelRecommendation row, Journey journey, HotelRecommendationResult evaluation,
                       HotelRecommendationItem item, Hotel hotel, String activeKey) {
        row.setJourney(journey);
        row.setJourneyImpact(evaluation.forcingImpactId() == null
                ? null
                : journeyImpactRepository.getReferenceById(evaluation.forcingImpactId()));
        row.setHotel(hotel);
        row.setDecisionOutcome(evaluation.outcome());
        row.setRelevantStation(evaluation.relevantStation());
        row.setRankPosition(item.rank());
        row.setReason(HotelScoringService.truncate(item.reason()));
        row.setRecommendationStatus(RecommendationStatus.AVAILABLE);
        row.setActiveKey(activeKey);
    }

    private void expire(HotelRecommendation row) {
        row.setRecommendationStatus(RecommendationStatus.EXPIRED);
        row.setActiveKey(null);
        hotelRecommendationRepository.save(row);
    }

    private static Long idOf(com.trainconcierge.entity.JourneyImpact impact) {
        return impact == null ? null : impact.getId();
    }

    private static Long idOf(Station station) {
        return station == null ? null : station.getId();
    }

    private static String activeKey(Long journeyId, Long hotelId) {
        return "J" + journeyId + ":H" + hotelId;
    }

    /**
     * Assessment of a single leg impact: whether it could be evaluated and
     * whether it forces an overnight stay.
     */
    private record LegAssessment(
            boolean determinable,
            boolean forcesOvernight,
            Station strandedStation,
            JourneyLeg leg,
            Integer delayMinutes,
            JourneyImpact impact
    ) {

        static LegAssessment undeterminable() {
            return new LegAssessment(false, false, null, null, null, null);
        }
    }
}
