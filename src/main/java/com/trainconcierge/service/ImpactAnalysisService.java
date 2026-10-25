package com.trainconcierge.service;

import com.trainconcierge.config.TrainConciergeProperties;
import com.trainconcierge.entity.DisruptionEvent;
import com.trainconcierge.entity.EventState;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.Journey;
import com.trainconcierge.entity.JourneyLeg;
import com.trainconcierge.repository.DisruptionEventRepository;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.JourneyRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One journey impact-analysis cycle.
 *
 * <p>It considers the ongoing disruptions (Phase 4) and the journeys whose
 * travel date falls inside the configured window, then hands each candidate
 * journey to {@link JourneyImpactService}. Every journey is reconciled in its
 * own transaction, so one failure cannot roll back the others and is reported as
 * a failure count instead. Safe to call from tests or the manual API; it never
 * depends on the Phase 4 scheduler being active.</p>
 *
 * <p>A journey is a candidate when either it uses a train that currently has an
 * ongoing disruption, or it already carries an ACTIVE impact. The second case is
 * what lets a resolved disruption close its impacts on the next run.</p>
 */
@Service
public class ImpactAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ImpactAnalysisService.class);

    /** Both states mean the disruption is still ongoing. */
    private static final List<EventState> ONGOING_EVENT_STATES =
            List.of(EventState.ACTIVE, EventState.ACKNOWLEDGED);

    private final JourneyImpactService journeyImpactService;
    private final JourneyRepository journeyRepository;
    private final JourneyImpactRepository journeyImpactRepository;
    private final DisruptionEventRepository disruptionEventRepository;
    private final TrainConciergeProperties properties;

    public ImpactAnalysisService(JourneyImpactService journeyImpactService,
                                 JourneyRepository journeyRepository,
                                 JourneyImpactRepository journeyImpactRepository,
                                 DisruptionEventRepository disruptionEventRepository,
                                 TrainConciergeProperties properties) {
        this.journeyImpactService = journeyImpactService;
        this.journeyRepository = journeyRepository;
        this.journeyImpactRepository = journeyImpactRepository;
        this.disruptionEventRepository = disruptionEventRepository;
        this.properties = properties;
    }

    /**
     * Runs a full analysis cycle over the configured service-date window.
     * Missing train status data is never interpreted as on time: only the
     * explicitly ongoing disruption events are used.
     */
    public ImpactAnalysisResult runAnalysis() {
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(properties.getImpact().getLookbackDays());
        LocalDate to = today.plusDays(properties.getImpact().getForwardDays());

        List<DisruptionEvent> ongoing = disruptionEventRepository.findEventsByStateWithTrain(ONGOING_EVENT_STATES);
        Set<Long> disruptedTrainIds = new LinkedHashSet<>();
        for (DisruptionEvent event : ongoing) {
            disruptedTrainIds.add(event.getTrain().getId());
        }

        Set<Long> candidateJourneyIds = new LinkedHashSet<>();
        if (!disruptedTrainIds.isEmpty()) {
            for (Journey journey : journeyRepository.findJourneysInDateRangeWithLegs(from, to)) {
                for (JourneyLeg leg : journey.getLegs()) {
                    if (disruptedTrainIds.contains(leg.getTrain().getId())) {
                        candidateJourneyIds.add(journey.getId());
                        break;
                    }
                }
            }
        }
        // Journeys that already carry an ACTIVE impact are always revisited, so a
        // disruption that has since resolved is reconciled to RESOLVED.
        candidateJourneyIds.addAll(journeyImpactRepository.findJourneyIdsByImpactState(ImpactState.ACTIVE));

        int created = 0;
        int updated = 0;
        int resolved = 0;
        int unchanged = 0;
        int failures = 0;

        for (Long journeyId : candidateJourneyIds) {
            try {
                JourneyImpactOutcome outcome = journeyImpactService.reconcileJourney(journeyId);
                created += outcome.created();
                updated += outcome.updated();
                resolved += outcome.resolved();
                unchanged += outcome.unchanged();
            } catch (RuntimeException ex) {
                failures++;
                log.error("Failed to analyse impacts for journey {}: {}", journeyId, ex.getMessage());
            }
        }

        ImpactAnalysisResult result = new ImpactAnalysisResult(
                candidateJourneyIds.size(), ongoing.size(), created, updated, resolved, unchanged, failures);
        if (log.isInfoEnabled()) {
            log.info("Impact analysis finished: journeysInspected={}, eventsInspected={}, created={}, "
                            + "updated={}, resolved={}, unchanged={}, failures={}",
                    result.journeysInspected(), result.eventsInspected(), result.impactsCreated(),
                    result.impactsUpdated(), result.impactsResolved(), result.impactsUnchanged(), result.failures());
        }
        return result;
    }
}
