package com.trainconcierge.service;

import com.trainconcierge.config.TrainConciergeProperties;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.JourneyLeg;
import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRecommendation;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStatus;
import com.trainconcierge.exception.ResourceNotFoundException;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.JourneyLegRepository;
import com.trainconcierge.repository.TrainRecommendationRepository;
import com.trainconcierge.repository.TrainStatusRepository;
import com.trainconcierge.repository.TrainStopRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generates the deterministic alternative-train recommendations for one journey
 * impact (Phase 6). Reuses the master data of Phase 1 (trains, stops, timetable),
 * the journeys of Phase 2, the simulated statuses of Phase 3 and the impacts of
 * Phase 5. No AI, no ML, no external or live railway API is contacted.
 *
 * <h2>Eligibility (candidate filter)</h2>
 * A train is a candidate for an affected leg when it is active and stops at the
 * leg's origin <em>and</em> destination in that order for the journey's service
 * date. The candidate is then rejected if any of these hold:
 * <ul>
 *   <li>it is the disrupted train itself ({@code ORIGINAL_TRAIN});</li>
 *   <li>its scheduled departure or arrival is missing
 *       ({@code MISSING_TIMETABLE_DATA});</li>
 *   <li>its simulated status for the service date is CANCELLED
 *       ({@code CANDIDATE_CANCELLED});</li>
 *   <li>its scheduled departure has already passed
 *       ({@code ALREADY_DEPARTED});</li>
 *   <li>the leg has a following leg and the candidate cannot make the connection
 *       within the configured transfer buffer ({@code CONNECTION_NOT_FEASIBLE}).</li>
 * </ul>
 * A candidate with no simulated status row is <em>not</em> assumed to be on
 * time: it is kept, flagged as unknown and ranked below candidates with a known
 * status.
 *
 * <h2>Idempotency / history</h2>
 * Each impact keeps one AVAILABLE row per (journey, leg, impact, candidate
 * train) through the {@code active_key} unique index. Regeneration refreshes the
 * existing AVAILABLE rows and marks as EXPIRED the ones that are no longer
 * feasible; nothing is ever deleted and no journey is ever modified.
 */
@Service
public class ImpactRecommendationService {

    private static final int MINUTES_PER_DAY = 24 * 60;

    /** Candidate exclusion reason codes reported in the run summary. */
    private static final String EX_ORIGINAL_TRAIN = "ORIGINAL_TRAIN";
    private static final String EX_MISSING_TIMETABLE = "MISSING_TIMETABLE_DATA";
    private static final String EX_CANDIDATE_CANCELLED = "CANDIDATE_CANCELLED";
    private static final String EX_ALREADY_DEPARTED = "ALREADY_DEPARTED";
    private static final String EX_CONNECTION_NOT_FEASIBLE = "CONNECTION_NOT_FEASIBLE";

    private final JourneyImpactRepository journeyImpactRepository;
    private final JourneyLegRepository journeyLegRepository;
    private final TrainStopRepository trainStopRepository;
    private final TrainStatusRepository trainStatusRepository;
    private final TrainRecommendationRepository trainRecommendationRepository;
    private final RecommendationScoringService scoringService;
    private final TrainConciergeProperties properties;
    private final Clock clock;

    public ImpactRecommendationService(JourneyImpactRepository journeyImpactRepository,
                                       JourneyLegRepository journeyLegRepository,
                                       TrainStopRepository trainStopRepository,
                                       TrainStatusRepository trainStatusRepository,
                                       TrainRecommendationRepository trainRecommendationRepository,
                                       RecommendationScoringService scoringService,
                                       TrainConciergeProperties properties,
                                       Clock clock) {
        this.journeyImpactRepository = journeyImpactRepository;
        this.journeyLegRepository = journeyLegRepository;
        this.trainStopRepository = trainStopRepository;
        this.trainStatusRepository = trainStatusRepository;
        this.trainRecommendationRepository = trainRecommendationRepository;
        this.scoringService = scoringService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Recomputes the recommendations of one impact inside its own transaction. */
    @Transactional
    public RecommendationRunResult reconcileImpact(Long impactId) {
        JourneyImpact impact = journeyImpactRepository.findById(impactId)
                .orElseThrow(() -> new ResourceNotFoundException("Journey impact not found with id: " + impactId));
        return reconcile(impact);
    }

    // ------------------------------------------------------------------
    // Reconciliation
    // ------------------------------------------------------------------

    private RecommendationRunResult reconcile(JourneyImpact impact) {
        Map<String, Integer> exclusions = new LinkedHashMap<>();
        List<TrainRecommendation> active = trainRecommendationRepository
                .findActiveForImpact(impact.getId(), RecommendationStatus.AVAILABLE);

        // An impact that is no longer ongoing, or is not tied to one leg, offers
        // nothing; whatever is still AVAILABLE is expired.
        if (impact.getImpactState() != ImpactState.ACTIVE) {
            int expired = expire(active, Set.of(), exclusions);
            return new RecommendationRunResult(1, 0, 0, 0, expired, 0, 0, 0,
                    exclusions, "NO_ELIGIBLE_ACTIVE_IMPACT");
        }
        if (impact.getJourneyLeg() == null) {
            int expired = expire(active, Set.of(), exclusions);
            return new RecommendationRunResult(1, 0, 0, 0, expired, 0, 0, 0,
                    exclusions, "IMPACT_NOT_LEG_SPECIFIC");
        }

        JourneyLeg leg = impact.getJourneyLeg();
        Journey journey = impact.getJourney();
        LocalDate serviceDate = journey.getJourneyDate();
        JourneyLeg nextLeg = findNextLeg(leg);

        List<RecommendationCandidate> candidates = collectCandidates(impact, leg, nextLeg, serviceDate, exclusions);
        int evaluated = candidates.size() + exclusions.values().stream().mapToInt(Integer::intValue).sum();

        List<RecommendationCandidate> ranked = candidates.stream()
                .sorted(scoringService.ranking())
                .toList();

        int max = properties.getRecommendation().getMaxResultsPerImpact();
        List<RecommendationCandidate> selected = ranked.size() > max ? ranked.subList(0, max) : ranked;

        Map<String, RecommendationCandidate> desired = new LinkedHashMap<>();
        for (RecommendationCandidate candidate : selected) {
            desired.put(activeKey(impact, candidate.train()), candidate);
        }

        Map<String, TrainRecommendation> activeByKey = new LinkedHashMap<>();
        for (TrainRecommendation recommendation : active) {
            if (recommendation.getActiveKey() != null) {
                activeByKey.put(recommendation.getActiveKey(), recommendation);
            }
        }

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Map.Entry<String, RecommendationCandidate> entry : desired.entrySet()) {
            RecommendationCandidate candidate = entry.getValue();
            TrainRecommendation existing = activeByKey.get(entry.getKey());
            if (existing == null) {
                trainRecommendationRepository.save(newRecommendation(impact, candidate, entry.getKey()));
                created++;
            } else if (refresh(existing, impact, candidate)) {
                trainRecommendationRepository.save(existing);
                updated++;
            } else {
                unchanged++;
            }
        }

        int expired = expire(active, desired.keySet(), exclusions);

        String reason = reason(selected, exclusions, evaluated);
        return new RecommendationRunResult(1, evaluated, created, updated, expired, unchanged,
                selected.size(), 0, exclusions, reason);
    }

    // ------------------------------------------------------------------
    // Candidate search and feasibility
    // ------------------------------------------------------------------

    private List<RecommendationCandidate> collectCandidates(JourneyImpact impact,
                                                            JourneyLeg leg,
                                                            JourneyLeg nextLeg,
                                                            LocalDate serviceDate,
                                                            Map<String, Integer> exclusions) {
        List<Object[]> rows = trainStopRepository.findCandidateTrains(
                leg.getSourceStation().getStationCode(), leg.getDestinationStation().getStationCode());

        List<RecommendationCandidate> candidates = new ArrayList<>();
        for (Object[] row : rows) {
            Train train = (Train) row[0];
            LocalTime departure = (LocalTime) row[3];
            Integer departureDayOffset = (Integer) row[4];
            LocalTime arrival = (LocalTime) row[5];
            Integer arrivalDayOffset = (Integer) row[6];

            if (train.getId().equals(leg.getTrain().getId())) {
                count(exclusions, EX_ORIGINAL_TRAIN);
                continue;
            }
            if (departure == null || arrival == null) {
                count(exclusions, EX_MISSING_TIMETABLE);
                continue;
            }

            TrainStatus status = trainStatusRepository
                    .findByTrainIdAndServiceDate(train.getId(), serviceDate).orElse(null);
            if (status != null && status.getStatus() == TrainRunningStatus.CANCELLED) {
                count(exclusions, EX_CANDIDATE_CANCELLED);
                continue;
            }
            boolean statusKnown = status != null;
            TrainRunningStatus candidateStatus = status == null ? null : status.getStatus();
            Integer candidateDelay = status == null ? null : status.getDelayMinutes();

            if (hasDeparted(serviceDate, departure, departureDayOffset)) {
                count(exclusions, EX_ALREADY_DEPARTED);
                continue;
            }

            if (nextLeg != null) {
                if (nextLeg.getDepartureTime() == null) {
                    count(exclusions, EX_MISSING_TIMETABLE);
                    continue;
                }
                if (!connectionFeasible(arrival, candidateDelay, nextLeg.getDepartureTime())) {
                    count(exclusions, EX_CONNECTION_NOT_FEASIBLE);
                    continue;
                }
            }

            Integer additionalMinutes = additionalMinutes(leg, departure, departureDayOffset,
                    arrival, arrivalDayOffset);

            candidates.add(new RecommendationCandidate(
                    train, departure, departureDayOffset, arrival, arrivalDayOffset,
                    true, 0, additionalMinutes, statusKnown, candidateStatus, candidateDelay));
        }
        return candidates;
    }

    /**
     * True when the candidate's scheduled departure on the given service date is
     * already in the past. A future service date is never "departed"; a past
     * service date always is.
     */
    private boolean hasDeparted(LocalDate serviceDate, LocalTime departure, Integer dayOffset) {
        LocalDate departureDate = serviceDate.plusDays(dayOffset == null ? 0 : dayOffset);
        LocalDate today = LocalDate.now(clock);
        if (departureDate.isAfter(today)) {
            return false;
        }
        if (departureDate.isBefore(today)) {
            return true;
        }
        return departure.isBefore(LocalTime.now(clock));
    }

    /**
     * The candidate must reach the connection station at least
     * {@code min-transfer-buffer-minutes} before the next leg departs. A single
     * midnight crossing is handled with the same convention as Phase 5 by adding
     * one day when the departure time of day is earlier than the arrival.
     */
    private boolean connectionFeasible(LocalTime arrival, Integer candidateDelay, LocalTime nextDeparture) {
        int arrivalMinutes = minutesOfDay(arrival) + (candidateDelay == null ? 0 : candidateDelay);
        int departureMinutes = minutesOfDay(nextDeparture);
        if (departureMinutes < arrivalMinutes) {
            departureMinutes += MINUTES_PER_DAY;
        }
        int buffer = departureMinutes - arrivalMinutes;
        return buffer >= properties.getRecommendation().getMinTransferBufferMinutes();
    }

    /**
     * Estimated extra minutes versus the passenger's planned leg, computed from
     * the scheduled durations only. {@code null} when either duration cannot be
     * derived from the available timetable data.
     */
    private static Integer additionalMinutes(JourneyLeg leg,
                                             LocalTime departure,
                                             Integer departureDayOffset,
                                             LocalTime arrival,
                                             Integer arrivalDayOffset) {
        if (leg.getDepartureTime() == null || leg.getArrivalTime() == null) {
            return null;
        }
        int candidateDuration = durationMinutes(departure, departureDayOffset, arrival, arrivalDayOffset);
        int plannedDuration = durationMinutes(leg.getDepartureTime(), 0, leg.getArrivalTime(), 0);
        if (candidateDuration < 0 || plannedDuration < 0) {
            return null;
        }
        return candidateDuration - plannedDuration;
    }

    private static int durationMinutes(LocalTime departure, Integer departureDayOffset,
                                       LocalTime arrival, Integer arrivalDayOffset) {
        int start = minutesOfDay(departure) + (departureDayOffset == null ? 0 : departureDayOffset) * MINUTES_PER_DAY;
        int end = minutesOfDay(arrival) + (arrivalDayOffset == null ? 0 : arrivalDayOffset) * MINUTES_PER_DAY;
        return end - start;
    }

    private JourneyLeg findNextLeg(JourneyLeg leg) {
        List<JourneyLeg> legs = journeyLegRepository.findLegsWithDetails(leg.getJourney().getId());
        for (int index = 0; index < legs.size(); index++) {
            if (legs.get(index).getId().equals(leg.getId())) {
                return index + 1 < legs.size() ? legs.get(index + 1) : null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Persistence helpers
    // ------------------------------------------------------------------

    private TrainRecommendation newRecommendation(JourneyImpact impact,
                                                  RecommendationCandidate candidate,
                                                  String activeKey) {
        TrainRecommendation recommendation = new TrainRecommendation();
        apply(recommendation, impact, candidate, activeKey);
        return recommendation;
    }

    /** @return true when a ranking-relevant field changed */
    private boolean refresh(TrainRecommendation recommendation,
                            JourneyImpact impact,
                            RecommendationCandidate candidate) {
        long newScore = scoringService.score(candidate);
        String newExplanation = explanation(impact, candidate);

        boolean changed = recommendation.getScore() != newScore
                || !Objects.equals(recommendation.getAdditionalMinutes(), candidate.additionalMinutes())
                || recommendation.isStatusKnown() != candidate.statusKnown()
                || recommendation.getCandidateStatus() != candidate.candidateStatus()
                || !Objects.equals(recommendation.getCandidateDelayMinutes(), candidate.candidateDelayMinutes())
                || !Objects.equals(recommendation.getExplanation(), newExplanation);

        if (changed) {
            apply(recommendation, impact, candidate, recommendation.getActiveKey());
        }
        return changed;
    }

    private void apply(TrainRecommendation recommendation,
                       JourneyImpact impact,
                       RecommendationCandidate candidate,
                       String activeKey) {
        recommendation.setJourney(impact.getJourney());
        recommendation.setJourneyLeg(impact.getJourneyLeg());
        recommendation.setJourneyImpact(impact);
        recommendation.setDisruptionEvent(impact.getDisruptionEvent());
        recommendation.setImpactType(impact.getImpactType());
        recommendation.setTrain(candidate.train());
        recommendation.setServiceDate(impact.getJourney().getJourneyDate());
        recommendation.setDepartureTime(candidate.departureTime());
        recommendation.setDepartureDayOffset(candidate.departureDayOffset());
        recommendation.setArrivalTime(candidate.arrivalTime());
        recommendation.setArrivalDayOffset(candidate.arrivalDayOffset());
        recommendation.setTransferCount(candidate.transferCount());
        recommendation.setAdditionalMinutes(candidate.additionalMinutes());
        recommendation.setScore(scoringService.score(candidate));
        recommendation.setRecommendationStatus(RecommendationStatus.AVAILABLE);
        recommendation.setStatusKnown(candidate.statusKnown());
        recommendation.setCandidateStatus(candidate.candidateStatus());
        recommendation.setCandidateDelayMinutes(candidate.candidateDelayMinutes());
        recommendation.setExplanation(explanation(impact, candidate));
        recommendation.setActiveKey(activeKey);
    }

    /**
     * Marks as EXPIRED every AVAILABLE recommendation whose key is not in the
     * desired set, releasing its unique key but keeping the row as history.
     */
    private int expire(List<TrainRecommendation> active, Set<String> desiredKeys, Map<String, Integer> exclusions) {
        int expired = 0;
        for (TrainRecommendation recommendation : active) {
            boolean stillWanted = recommendation.getActiveKey() != null
                    && desiredKeys.contains(recommendation.getActiveKey());
            if (!stillWanted) {
                recommendation.setRecommendationStatus(RecommendationStatus.EXPIRED);
                recommendation.setActiveKey(null);
                trainRecommendationRepository.save(recommendation);
                expired++;
            }
        }
        return expired;
    }

    // ------------------------------------------------------------------
    // Explanations and reasons
    // ------------------------------------------------------------------

    private static String explanation(JourneyImpact impact, RecommendationCandidate candidate) {
        JourneyLeg leg = impact.getJourneyLeg();
        Train train = candidate.train();
        StringBuilder text = new StringBuilder();

        if (leg == null) {
            text.append("Train ").append(train.getTrainNumber())
                    .append(" is a feasible replacement for journey ")
                    .append(impact.getJourney().getJourneyReference()).append('.');
            return truncate(text);
        }

        text.append("Train ").append(train.getTrainNumber())
                .append(" (").append(train.getName()).append(") can replace leg ")
                .append(leg.getLegSequence()).append(" (")
                .append(leg.getSourceStation().getStationCode()).append(" -> ")
                .append(leg.getDestinationStation().getStationCode()).append(") on ")
                .append(impact.getJourney().getJourneyDate()).append(": departs ")
                .append(formatTime(candidate.departureTime(), candidate.departureDayOffset()))
                .append(", arrives ")
                .append(formatTime(candidate.arrivalTime(), candidate.arrivalDayOffset()));

        if (candidate.additionalMinutes() == null) {
            text.append("; additional time could not be computed from the available timetable data");
        } else {
            int extra = candidate.additionalMinutes();
            text.append("; ").append(extra >= 0 ? "+" + extra + " min" : extra + " min")
                    .append(" versus the planned leg");
        }

        text.append("; ").append(candidate.transferCount()).append(" transfer(s)");

        if (candidate.statusKnown()) {
            text.append("; live status for the service date is known (")
                    .append(candidate.candidateStatus());
            if (candidate.candidateDelayMinutes() != null) {
                text.append(", ").append(candidate.candidateDelayMinutes()).append(" min delay");
            }
            text.append(')');
        } else {
            text.append("; live status for the service date is unknown, so this candidate is ranked "
                    + "below trains with a known status");
        }

        text.append('.');
        return truncate(text);
    }

    private static String reason(List<RecommendationCandidate> selected,
                                 Map<String, Integer> exclusions,
                                 int evaluated) {
        if (!selected.isEmpty()) {
            return null;
        }
        if (evaluated == 0) {
            return "NO_FEASIBLE_ALTERNATIVES";
        }
        int disrupted = exclusions.getOrDefault(EX_ORIGINAL_TRAIN, 0)
                + exclusions.getOrDefault(EX_CANDIDATE_CANCELLED, 0);
        if (disrupted == evaluated) {
            return "ALL_CANDIDATES_DISRUPTED";
        }
        boolean onlyMissingData = exclusions.getOrDefault(EX_MISSING_TIMETABLE, 0) > 0
                && exclusions.getOrDefault(EX_CANDIDATE_CANCELLED, 0) == 0
                && exclusions.getOrDefault(EX_CONNECTION_NOT_FEASIBLE, 0) == 0
                && exclusions.getOrDefault(EX_ALREADY_DEPARTED, 0) == 0;
        if (onlyMissingData) {
            return "INSUFFICIENT_TIMETABLE_DATA";
        }
        return "NO_FEASIBLE_ALTERNATIVES";
    }

    private static String formatTime(LocalTime time, Integer dayOffset) {
        if (time == null) {
            return "unknown";
        }
        String base = "%02d:%02d".formatted(time.getHour(), time.getMinute());
        return dayOffset == null || dayOffset == 0 ? base : base + " (day " + dayOffset + ")";
    }

    private static String truncate(StringBuilder text) {
        String value = text.toString();
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static int minutesOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static void count(Map<String, Integer> exclusions, String key) {
        exclusions.merge(key, 1, Integer::sum);
    }

    private static String activeKey(JourneyImpact impact, Train train) {
        long legPart = impact.getJourneyLeg() == null ? 0L : impact.getJourneyLeg().getId();
        return "J%d:L%d:I%d:T%d".formatted(impact.getJourney().getId(), legPart,
                impact.getId(), train.getId());
    }
}
