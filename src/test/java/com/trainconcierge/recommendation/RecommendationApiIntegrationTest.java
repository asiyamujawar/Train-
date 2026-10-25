package com.trainconcierge.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trainconcierge.dto.JourneyLegRequest;
import com.trainconcierge.dto.JourneyRequest;
import com.trainconcierge.dto.TrainStatusRequest;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.Passenger;
import com.trainconcierge.entity.RecommendationStatus;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRecommendation;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStop;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.PassengerRepository;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRecommendationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStopRepository;
import com.trainconcierge.service.AlternativeRecommendationService;
import com.trainconcierge.service.DisruptionMonitoringService;
import com.trainconcierge.service.ImpactAnalysisService;
import com.trainconcierge.service.JourneyService;
import com.trainconcierge.service.RecommendationRunResult;
import com.trainconcierge.service.TrainStatusService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 6 integration tests.
 *
 * <p>They run against the real MySQL schema created by Flyway (V1-V8) and reuse
 * the Phase 1-5 pipeline: master data -> simulated status -> disruption event ->
 * journey impact -> alternative-train recommendation. The scheduled monitor is
 * disabled, every test rolls back and no external service is contacted.</p>
 *
 * <p>{@code max-results-per-impact} is set to 3 so the stored-result limit is
 * exercised without creating an unrealistic number of trains.</p>
 */
@SpringBootTest(properties = {
        "trainconcierge.monitoring.enabled=false",
        "trainconcierge.disruption.delay-threshold-minutes=30",
        "trainconcierge.recommendation.max-results-per-impact=3",
        "trainconcierge.recommendation.min-transfer-buffer-minutes=15"
})
@AutoConfigureMockMvc
@Transactional
class RecommendationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private TrainRepository trainRepository;

    @Autowired
    private TrainStopRepository trainStopRepository;

    @Autowired
    private PassengerRepository passengerRepository;

    @Autowired
    private JourneyImpactRepository journeyImpactRepository;

    @Autowired
    private TrainRecommendationRepository trainRecommendationRepository;

    @Autowired
    private JourneyService journeyService;

    @Autowired
    private TrainStatusService trainStatusService;

    @Autowired
    private DisruptionMonitoringService disruptionMonitoringService;

    @Autowired
    private ImpactAnalysisService impactAnalysisService;

    @Autowired
    private AlternativeRecommendationService alternativeRecommendationService;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final LocalDate today = LocalDate.now();
    private final LocalDate journeyDate = today.plusDays(2);

    // 1. Cancelled train -> feasible alternative
    @Test
    @DisplayName("a cancelled leg produces a feasible alternative replacement train")
    void cancelledTrainProducesFeasibleAlternative() throws Exception {
        Station s1 = newStation("AA");
        Station s2 = newStation("AB");
        long disrupted = newTrain("830001", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        long replacement = newTrain("830002", s1, s2);
        addStop(replacement, s1, 1, null, "11:00:00", 0);
        addStop(replacement, s2, 2, "13:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        setStatus(journeyDate, replacement, TrainRunningStatus.ON_TIME, 0, null);

        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(result.recommendationsReturned()).isEqualTo(1);
        assertThat(result.reason()).isNull();
        assertThat(result.exclusionReasons()).containsEntry("ORIGINAL_TRAIN", 1);

        List<TrainRecommendation> available = recommendations(impact.getId(), RecommendationStatus.AVAILABLE);
        assertThat(available).hasSize(1);
        TrainRecommendation recommendation = available.get(0);
        assertThat(recommendation.getTrain().getTrainNumber()).isEqualTo("830002");
        assertThat(recommendation.getRecommendationStatus()).isEqualTo(RecommendationStatus.AVAILABLE);
        assertThat(recommendation.isStatusKnown()).isTrue();
        assertThat(recommendation.getCandidateStatus()).isEqualTo(TrainRunningStatus.ON_TIME);
        assertThat(recommendation.getAdditionalMinutes()).isZero();
        assertThat(recommendation.getExplanation()).contains("830002").contains("can replace leg 1");

        mockMvc.perform(get("/api/v1/impacts/{impactId}/recommendations", impact.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].replacementTrainNumber").value("830002"))
                .andExpect(jsonPath("$.content[0].recommendationStatus").value("AVAILABLE"))
                .andExpect(jsonPath("$.content[0].liveStatusKnown").value(true))
                .andExpect(jsonPath("$.content[0].explanation").isNotEmpty());
    }

    // 2. Journey-level impact cannot carry a leg replacement
    @Test
    @DisplayName("a journey-level impact yields no recommendation and an explicit reason")
    void journeyLevelImpactHasNoRecommendation() {
        Station s1 = newStation("BA");
        Station s2 = newStation("BB");
        long disrupted = newTrain("830003", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        runPipeline();

        JourneyImpact itinerary = activeImpact(journeyId, ImpactType.ITINERARY_DISRUPTED);
        assertThat(itinerary.getJourneyLeg()).isNull();

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(itinerary.getId());

        assertThat(result.recommendationsReturned()).isZero();
        assertThat(result.reason()).isEqualTo("IMPACT_NOT_LEG_SPECIFIC");
        assertThat(recommendations(itinerary.getId(), RecommendationStatus.AVAILABLE)).isEmpty();
    }

    // 3. Original and cancelled candidates are excluded
    @Test
    @DisplayName("the disrupted train and any cancelled candidate are never recommended")
    void originalAndCancelledCandidatesAreExcluded() {
        Station s1 = newStation("CA");
        Station s2 = newStation("CB");
        long disrupted = newTrain("830004", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        long usable = newTrain("830005", s1, s2);
        addStop(usable, s1, 1, null, "11:00:00", 0);
        addStop(usable, s2, 2, "13:00:00", null, 0);

        long alsoCancelled = newTrain("830006", s1, s2);
        addStop(alsoCancelled, s1, 1, null, "11:30:00", 0);
        addStop(alsoCancelled, s2, 2, "13:30:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        setStatus(journeyDate, usable, TrainRunningStatus.ON_TIME, 0, null);
        setStatus(journeyDate, alsoCancelled, TrainRunningStatus.CANCELLED, 0, "Cancelled too");

        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(recommendedTrainNumbers(impact.getId())).containsExactly("830005");
        assertThat(result.exclusionReasons()).containsEntry("ORIGINAL_TRAIN", 1);
        assertThat(result.exclusionReasons()).containsEntry("CANDIDATE_CANCELLED", 1);
    }

    // 4. Ranking: known status before unknown, lower delay first
    @Test
    @DisplayName("candidates are ordered by known status and then by the known delay")
    void knownStatusRanksAboveUnknownAndLowerDelayFirst() {
        Station s1 = newStation("DA");
        Station s2 = newStation("DB");
        long disrupted = newTrain("830007", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        long onTime = newTrain("830008", s1, s2);
        addStop(onTime, s1, 1, null, "11:00:00", 0);
        addStop(onTime, s2, 2, "13:00:00", null, 0);

        long delayed = newTrain("830009", s1, s2);
        addStop(delayed, s1, 1, null, "11:00:00", 0);
        addStop(delayed, s2, 2, "13:00:00", null, 0);

        long unknown = newTrain("830010", s1, s2);
        addStop(unknown, s1, 1, null, "11:00:00", 0);
        addStop(unknown, s2, 2, "13:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        setStatus(journeyDate, onTime, TrainRunningStatus.ON_TIME, 0, null);
        setStatus(journeyDate, delayed, TrainRunningStatus.DELAYED, 40, "Late");
        // `unknown` deliberately has no status row: it must not be treated as on time.

        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(recommendedTrainNumbers(impact.getId()))
                .containsExactly("830008", "830009", "830010");

        List<TrainRecommendation> available = recommendations(impact.getId(), RecommendationStatus.AVAILABLE);
        assertThat(available.get(0).getScore()).isEqualTo(0L);
        assertThat(available.get(1).getScore()).isEqualTo(40L);
        assertThat(available.get(1).getCandidateStatus()).isEqualTo(TrainRunningStatus.DELAYED);

        TrainRecommendation unknownRecommendation = available.get(2);
        assertThat(unknownRecommendation.isStatusKnown()).isFalse();
        assertThat(unknownRecommendation.getCandidateStatus()).isNull();
        assertThat(unknownRecommendation.getExplanation()).contains("unknown");
    }

    // 5. Direction and partial coverage
    @Test
    @DisplayName("a single-endpoint or reversed-order train is never presented as a full replacement")
    void directionAndPartialCoverageAreEnforced() {
        Station s1 = newStation("EA");
        Station s2 = newStation("EB");
        long disrupted = newTrain("830011", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        long usable = newTrain("830012", s1, s2);
        addStop(usable, s1, 1, null, "11:00:00", 0);
        addStop(usable, s2, 2, "13:00:00", null, 0);

        // stops only at the origin
        long originOnly = newTrain("830013", s1, s2);
        addStop(originOnly, s1, 1, null, "11:10:00", 0);

        // stops at both endpoints but in the wrong order
        long reversed = newTrain("830014", s1, s2);
        addStop(reversed, s2, 1, null, "11:20:00", 0);
        addStop(reversed, s1, 2, "12:20:00", "12:30:00", 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        setStatus(journeyDate, usable, TrainRunningStatus.ON_TIME, 0, null);

        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(recommendedTrainNumbers(impact.getId())).containsExactly("830012");
    }

    // 6. Connection feasibility against the following leg
    @Test
    @DisplayName("only candidates that make the next connection within the buffer are recommended")
    void connectionFeasibilityIsEnforced() {
        Station s1 = newStation("FA");
        Station s2 = newStation("FB");
        Station s3 = newStation("FC");
        long leg1Train = newTrain("830015", s1, s2);
        addStop(leg1Train, s1, 1, null, "08:00:00", 0);
        addStop(leg1Train, s2, 2, "10:00:00", "10:10:00", 0);
        long leg2Train = newTrain("830016", s2, s3);
        addStop(leg2Train, s2, 1, null, "14:00:00", 0);
        addStop(leg2Train, s3, 2, "16:00:00", null, 0);

        long feasible = newTrain("830017", s1, s2);
        addStop(feasible, s1, 1, null, "11:00:00", 0);
        addStop(feasible, s2, 2, "13:00:00", null, 0);

        long tooLate = newTrain("830018", s1, s2);
        addStop(tooLate, s1, 1, null, "11:10:00", 0);
        addStop(tooLate, s2, 2, "13:50:00", null, 0);

        long journeyId = createJourney(journeyDate,
                new LegSpec(leg1Train, s1.getId(), s2.getId()),
                new LegSpec(leg2Train, s2.getId(), s3.getId()));
        setStatus(journeyDate, leg1Train, TrainRunningStatus.DELAYED, 30, "Late");
        setStatus(journeyDate, feasible, TrainRunningStatus.ON_TIME, 0, null);

        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.DELAY);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(recommendedTrainNumbers(impact.getId())).containsExactly("830017");
        assertThat(result.exclusionReasons()).containsEntry("CONNECTION_NOT_FEASIBLE", 1);
    }

    // 7. Cross-midnight connection
    @Test
    @DisplayName("a connection to a next-day departure is evaluated with the midnight convention")
    void crossMidnightConnectionIsHandled() {
        Station s1 = newStation("GA");
        Station s2 = newStation("GB");
        Station s3 = newStation("GC");
        long leg1Train = newTrain("830019", s1, s2);
        addStop(leg1Train, s1, 1, null, "22:00:00", 0);
        addStop(leg1Train, s2, 2, "23:30:00", "23:35:00", 0);
        long leg2Train = newTrain("830020", s2, s3);
        addStop(leg2Train, s2, 1, null, "00:30:00", 1);
        addStop(leg2Train, s3, 2, "02:00:00", null, 1);

        long feasible = newTrain("830021", s1, s2);
        addStop(feasible, s1, 1, null, "22:00:00", 0);
        addStop(feasible, s2, 2, "23:00:00", null, 0);

        long tooLate = newTrain("830022", s1, s2);
        addStop(tooLate, s1, 1, null, "23:00:00", 0);
        addStop(tooLate, s2, 2, "00:20:00", null, 1);

        long journeyId = createJourney(journeyDate,
                new LegSpec(leg1Train, s1.getId(), s2.getId()),
                new LegSpec(leg2Train, s2.getId(), s3.getId()));
        setStatus(journeyDate, leg1Train, TrainRunningStatus.DELAYED, 30, "Late overnight");
        setStatus(journeyDate, feasible, TrainRunningStatus.ON_TIME, 0, null);

        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.DELAY);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(recommendedTrainNumbers(impact.getId())).containsExactly("830021");
        assertThat(result.exclusionReasons()).containsEntry("CONNECTION_NOT_FEASIBLE", 1);
    }

    // 8. Missing timetable data
    @Test
    @DisplayName("a candidate with a missing departure time yields INSUFFICIENT_TIMETABLE_DATA")
    void missingTimetableDataYieldsExplicitReason() throws Exception {
        Station s1 = newStation("HA");
        Station s2 = newStation("HB");
        long disrupted = newTrain("830023", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        long broken = newTrain("830024", s1, s2);
        // departure time deliberately missing -> no reliable departure exists
        addStop(broken, s1, 1, null, null, 0);
        addStop(broken, s2, 2, "13:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(result.recommendationsReturned()).isZero();
        assertThat(result.reason()).isEqualTo("INSUFFICIENT_TIMETABLE_DATA");
        assertThat(result.exclusionReasons()).containsEntry("MISSING_TIMETABLE_DATA", 1);
        assertThat(recommendations(impact.getId(), RecommendationStatus.AVAILABLE)).isEmpty();

        mockMvc.perform(post("/api/v1/impacts/{impactId}/recommendations/run", impact.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationsReturned").value(0))
                .andExpect(jsonPath("$.reason").value("INSUFFICIENT_TIMETABLE_DATA"))
                .andExpect(jsonPath("$.exclusionReasons.MISSING_TIMETABLE_DATA").value(1));
    }

    // 9. Only the disrupted train serves the leg
    @Test
    @DisplayName("when the only train serving the leg is the disrupted one, no alternative is returned")
    void onlyDisruptedTrainServesTheLeg() {
        Station s1 = newStation("IA");
        Station s2 = newStation("IB");
        long disrupted = newTrain("830025", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(result.recommendationsReturned()).isZero();
        assertThat(result.reason()).isEqualTo("ALL_CANDIDATES_DISRUPTED");
        assertThat(result.exclusionReasons()).containsEntry("ORIGINAL_TRAIN", 1);
    }

    // 10. Already-departed candidate
    @Test
    @DisplayName("a candidate whose departure already passed is not recommended")
    void alreadyDepartedCandidatesAreExcluded() {
        Station s1 = newStation("JA");
        Station s2 = newStation("JB");
        long disrupted = newTrain("830026", s1, s2);
        addStop(disrupted, s1, 1, null, "01:00:00", 0);
        addStop(disrupted, s2, 2, "03:00:00", null, 0);

        long departed = newTrain("830027", s1, s2);
        // departs at midnight on the service date, which is always in the past
        addStop(departed, s1, 1, null, "00:00:00", 0);
        addStop(departed, s2, 2, "02:00:00", null, 0);

        long journeyId = createJourney(today, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(today, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(result.recommendationsReturned()).isZero();
        assertThat(result.reason()).isEqualTo("NO_FEASIBLE_ALTERNATIVES");
        assertThat(result.exclusionReasons()).containsEntry("ALREADY_DEPARTED", 1);
    }

    // 11. Idempotency
    @Test
    @DisplayName("running the generation twice does not create duplicates")
    void repeatedGenerationIsIdempotent() throws Exception {
        Station s1 = newStation("KA");
        Station s2 = newStation("KB");
        long disrupted = newTrain("830028", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);
        long replacement = newTrain("830029", s1, s2);
        addStop(replacement, s1, 1, null, "11:00:00", 0);
        addStop(replacement, s2, 2, "13:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        setStatus(journeyDate, replacement, TrainRunningStatus.ON_TIME, 0, null);
        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        RecommendationRunResult first = alternativeRecommendationService.runForImpact(impact.getId());
        RecommendationRunResult second = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(first.recommendationsCreated()).isEqualTo(1);
        assertThat(second.recommendationsCreated()).isZero();
        assertThat(second.recommendationsUnchanged()).isEqualTo(1);
        assertThat(second.recommendationsReturned()).isEqualTo(1);
        assertThat(recommendations(impact.getId(), RecommendationStatus.AVAILABLE)).hasSize(1);

        mockMvc.perform(post("/api/v1/impacts/{impactId}/recommendations/run", impact.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendationsCreated").value(0))
                .andExpect(jsonPath("$.recommendationsReturned").value(1))
                .andExpect(jsonPath("$.failures").value(0));
    }

    // 12. Expiry keeps history
    @Test
    @DisplayName("a recommendation that stops being feasible becomes EXPIRED and is kept as history")
    void candidateCancellationExpiresRecommendationAndKeepsHistory() throws Exception {
        Station s1 = newStation("LA");
        Station s2 = newStation("LB");
        long disrupted = newTrain("830030", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);
        long replacement = newTrain("830031", s1, s2);
        addStop(replacement, s1, 1, null, "11:00:00", 0);
        addStop(replacement, s2, 2, "13:00:00", null, 0);

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        setStatus(journeyDate, replacement, TrainRunningStatus.ON_TIME, 0, null);
        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        alternativeRecommendationService.runForImpact(impact.getId());
        assertThat(recommendations(impact.getId(), RecommendationStatus.AVAILABLE)).hasSize(1);

        // The replacement is cancelled for the service date: it is no longer usable.
        setStatus(journeyDate, replacement, TrainRunningStatus.CANCELLED, 0, "Now cancelled");
        RecommendationRunResult second = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(second.recommendationsExpired()).isEqualTo(1);
        assertThat(second.recommendationsReturned()).isZero();
        assertThat(recommendations(impact.getId(), RecommendationStatus.AVAILABLE)).isEmpty();

        List<TrainRecommendation> history = recommendations(impact.getId(), RecommendationStatus.EXPIRED);
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getActiveKey()).isNull();
        assertThat(history.get(0).getTrain().getTrainNumber()).isEqualTo("830031");

        mockMvc.perform(get("/api/v1/impacts/{impactId}/recommendations", impact.getId())
                        .param("recommendationStatus", "EXPIRED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].recommendationStatus").value("EXPIRED"));
    }

    // 13. Result limit and pagination
    @Test
    @DisplayName("the stored-result limit is applied and the list endpoint paginates")
    void resultLimitAndPagination() throws Exception {
        Station s1 = newStation("MA");
        Station s2 = newStation("MB");
        long disrupted = newTrain("830032", s1, s2);
        addStop(disrupted, s1, 1, null, "08:00:00", 0);
        addStop(disrupted, s2, 2, "10:00:00", null, 0);

        for (int i = 0; i < 4; i++) {
            long candidate = newTrain("8300%02d".formatted(33 + i), s1, s2);
            addStop(candidate, s1, 1, null, "11:00:00", 0);
            addStop(candidate, s2, 2, "13:00:00", null, 0);
            setStatus(journeyDate, candidate, TrainRunningStatus.ON_TIME, 0, null);
        }

        long journeyId = createJourney(journeyDate, new LegSpec(disrupted, s1.getId(), s2.getId()));
        setStatus(journeyDate, disrupted, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        runPipeline();
        JourneyImpact impact = activeImpact(journeyId, ImpactType.CANCELLATION);

        RecommendationRunResult result = alternativeRecommendationService.runForImpact(impact.getId());

        assertThat(result.recommendationsReturned()).isEqualTo(3);
        assertThat(recommendedTrainNumbers(impact.getId()))
                .containsExactly("830033", "830034", "830035");

        mockMvc.perform(get("/api/v1/impacts/{impactId}/recommendations", impact.getId())
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(false));

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/recommendations", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].replacementTrainNumber").value("830033"));
    }

    // 14. Unknown identifiers
    @Test
    @DisplayName("unknown journey or impact ids return the standard 404 response")
    void unknownJourneyOrImpactReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/journeys/{journeyId}/recommendations", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/v1/impacts/{impactId}/recommendations", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(post("/api/v1/impacts/{impactId}/recommendations/run", 999_999_999L))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/impacts/{impactId}/recommendations", 999_999_999L)
                        .param("recommendationStatus", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest());
    }

    // 15. Regression across all phases
    @Test
    @DisplayName("Phase 0-5 endpoints still respond after the Phase 6 additions")
    void earlierPhaseEndpointsStillRespond() throws Exception {
        mockMvc.perform(get("/api/v1/stations")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/trains")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/journeys")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/disruptions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/impacts")).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/recommendations/run")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Phase 4 monitoring creates the disruption event, Phase 5 consumes it. */
    private void runPipeline() {
        disruptionMonitoringService.runMonitoring();
        impactAnalysisService.runAnalysis();
    }

    private JourneyImpact activeImpact(long journeyId, ImpactType type) {
        return journeyImpactRepository
                .findByJourneyIdAndImpactStateOrderByDetectedAtAscIdAsc(journeyId, ImpactState.ACTIVE)
                .stream()
                .filter(impact -> impact.getImpactType() == type)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No ACTIVE " + type + " impact found for journey " + journeyId));
    }

    private List<TrainRecommendation> recommendations(long impactId, RecommendationStatus status) {
        return trainRecommendationRepository.findActiveForImpact(impactId, status).stream()
                .sorted(Comparator.comparing(TrainRecommendation::getScore)
                        .thenComparing(recommendation -> recommendation.getTrain().getId()))
                .toList();
    }

    private List<String> recommendedTrainNumbers(long impactId) {
        return recommendations(impactId, RecommendationStatus.AVAILABLE).stream()
                .map(recommendation -> recommendation.getTrain().getTrainNumber())
                .toList();
    }

    private void setStatus(LocalDate serviceDate, long trainId, TrainRunningStatus status,
                           Integer delayMinutes, String reason) {
        trainStatusService.upsert(trainId, new TrainStatusRequest(
                serviceDate, status, delayMinutes, null, reason, null, null));
    }

    private long createJourney(LocalDate serviceDate, LegSpec... legs) {
        long passengerId = newPassenger();
        List<JourneyLegRequest> requests = new ArrayList<>();
        for (int i = 0; i < legs.length; i++) {
            LegSpec leg = legs[i];
            requests.add(new JourneyLegRequest(leg.trainId(), leg.sourceId(), leg.destinationId(), i + 1));
        }
        return journeyService.create(new JourneyRequest(passengerId, serviceDate, requests)).id();
    }

    private long newPassenger() {
        Passenger passenger = new Passenger();
        passenger.setFullName("Recommendation Tester " + SEQ.incrementAndGet());
        passenger.setEmail("recommendation-" + SEQ.get() + "-" + System.nanoTime() + "@example.com");
        passenger.setActive(true);
        return passengerRepository.save(passenger).getId();
    }

    private Station newStation(String prefix) {
        Station station = new Station();
        station.setStationCode(prefix + SEQ.incrementAndGet());
        station.setName("Station " + prefix + SEQ.get());
        station.setCity("Demo");
        station.setActive(true);
        return stationRepository.save(station);
    }

    private long newTrain(String number, Station source, Station destination) {
        Train train = new Train();
        train.setTrainNumber(number);
        train.setName("Recommendation Demo " + number);
        train.setSourceStation(source);
        train.setDestinationStation(destination);
        train.setActive(true);
        return trainRepository.save(train).getId();
    }

    private void addStop(long trainId, Station station, int sequence, String arrival, String departure,
                         int dayOffset) {
        TrainStop stop = new TrainStop();
        stop.setTrain(trainRepository.getReferenceById(trainId));
        stop.setStation(station);
        stop.setStopSequence(sequence);
        stop.setArrivalTime(arrival == null ? null : LocalTime.parse(arrival));
        stop.setDepartureTime(departure == null ? null : LocalTime.parse(departure));
        stop.setDayOffset(dayOffset);
        stop.setDistanceFromSourceKm(sequence * 100);
        trainStopRepository.save(stop);
    }

    private record LegSpec(long trainId, long sourceId, long destinationId) {
    }
}
