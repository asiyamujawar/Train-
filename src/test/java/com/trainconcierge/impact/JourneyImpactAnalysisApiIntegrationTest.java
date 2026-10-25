package com.trainconcierge.impact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.trainconcierge.dto.JourneyLegRequest;
import com.trainconcierge.dto.JourneyRequest;
import com.trainconcierge.dto.TrainStatusRequest;
import com.trainconcierge.entity.ImpactSeverity;
import com.trainconcierge.entity.ImpactState;
import com.trainconcierge.entity.ImpactType;
import com.trainconcierge.entity.JourneyImpact;
import com.trainconcierge.entity.Passenger;
import com.trainconcierge.entity.Station;
import com.trainconcierge.entity.Train;
import com.trainconcierge.entity.TrainRunningStatus;
import com.trainconcierge.entity.TrainStop;
import com.trainconcierge.repository.JourneyImpactRepository;
import com.trainconcierge.repository.PassengerRepository;
import com.trainconcierge.repository.StationRepository;
import com.trainconcierge.repository.TrainRepository;
import com.trainconcierge.repository.TrainStopRepository;
import com.trainconcierge.service.DisruptionMonitoringService;
import com.trainconcierge.service.ImpactAnalysisResult;
import com.trainconcierge.service.ImpactAnalysisService;
import com.trainconcierge.service.JourneyService;
import com.trainconcierge.service.TrainStatusService;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phase 5 integration tests.
 *
 * <p>They run against the real MySQL schema created by Flyway (V1-V7). The
 * scheduled monitor is disabled and every test rolls back, so the assertions are
 * deterministic. No external service is contacted.</p>
 */
@SpringBootTest(properties = {
        "trainconcierge.monitoring.enabled=false",
        "trainconcierge.disruption.delay-threshold-minutes=30"
})
@AutoConfigureMockMvc
@Transactional
class JourneyImpactAnalysisApiIntegrationTest {

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
    private JourneyService journeyService;

    @Autowired
    private TrainStatusService trainStatusService;

    @Autowired
    private DisruptionMonitoringService disruptionMonitoringService;

    @Autowired
    private ImpactAnalysisService impactAnalysisService;

    private static final AtomicInteger SEQ = new AtomicInteger();

    private final LocalDate journeyDate = LocalDate.now().plusDays(2);

    // 1. Cancelled train
    @Test
    @DisplayName("a journey using a cancelled train gets a CANCELLATION and an ITINERARY_DISRUPTED impact")
    void cancelledTrainImpact() throws Exception {
        Station s1 = newStation("CA");
        Station s2 = newStation("CB");
        long trainId = newTrain("810001", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);
        long journeyId = createJourney(new LegSpec(trainId, s1.getId(), s2.getId()));

        setStatus(trainId, TrainRunningStatus.CANCELLED, 0, "Cancelled");

        impactAnalysisAndMonitor();

        List<JourneyImpact> impacts = impactsOf(journeyId);
        assertThat(impacts).hasSize(2);
        assertThat(typesOf(impacts)).containsExactlyInAnyOrder(ImpactType.CANCELLATION, ImpactType.ITINERARY_DISRUPTED);
        assertThat(impacts).allMatch(i -> i.getSeverity() == ImpactSeverity.CRITICAL);
        assertThat(impacts).allMatch(i -> i.getImpactState() == ImpactState.ACTIVE);
        assertThat(impacts).allMatch(i -> i.getResolvedAt() == null);

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/impacts", journeyId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].impactType").exists())
                .andExpect(jsonPath("$.content[0].explanation").isNotEmpty());
    }

    // 2. Delayed train, no next leg
    @Test
    @DisplayName("a journey whose final leg is delayed gets a DELAY impact")
    void delayedFinalLegImpact() {
        Station s1 = newStation("DA");
        Station s2 = newStation("DB");
        long trainId = newTrain("810002", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);
        long journeyId = createJourney(new LegSpec(trainId, s1.getId(), s2.getId()));

        setStatus(trainId, TrainRunningStatus.DELAYED, 45, "Signal failure");

        impactAnalysisAndMonitor();

        List<JourneyImpact> impacts = impactsOf(journeyId);
        assertThat(impacts).hasSize(1);
        JourneyImpact impact = impacts.get(0);
        assertThat(impact.getImpactType()).isEqualTo(ImpactType.DELAY);
        assertThat(impact.getSeverity()).isEqualTo(ImpactSeverity.LOW);
        assertThat(impact.getDelayMinutes()).isEqualTo(45);
        assertThat(impact.getExplanation()).contains("final leg");
    }

    // 3. Delay threatens the next connection
    @Test
    @DisplayName("a delay beyond the transfer buffer raises a MISSED_CONNECTION impact")
    void delayThreateningConnection() {
        Station s1 = newStation("MA");
        Station s2 = newStation("MB");
        Station s3 = newStation("MC");
        long trainA = newTrain("810003", s1, s2);
        addStop(trainA, s1, 1, null, "08:00:00", 0);
        addStop(trainA, s2, 2, "10:00:00", "10:10:00", 0);
        long trainB = newTrain("810004", s2, s3);
        addStop(trainB, s2, 1, null, "10:30:00", 0);
        addStop(trainB, s3, 2, "12:00:00", null, 0);

        long journeyId = createJourney(
                new LegSpec(trainA, s1.getId(), s2.getId()),
                new LegSpec(trainB, s2.getId(), s3.getId()));

        setStatus(trainA, TrainRunningStatus.DELAYED, 45, "Late running");

        impactAnalysisAndMonitor();

        List<JourneyImpact> impacts = impactsOf(journeyId);
        assertThat(typesOf(impacts))
                .containsExactlyInAnyOrder(ImpactType.MISSED_CONNECTION, ImpactType.ITINERARY_DISRUPTED);
        JourneyImpact missed = impactOfType(impacts, ImpactType.MISSED_CONNECTION);
        assertThat(missed.getSeverity()).isEqualTo(ImpactSeverity.MEDIUM);
        assertThat(missed.getDelayMinutes()).isEqualTo(45);
        assertThat(missed.getJourneyLeg().getId()).isNotNull();
        assertThat(missed.getExplanation()).contains("buffer 30 min");
    }

    // 4. Delay does not threaten the next connection
    @Test
    @DisplayName("a delay inside the transfer buffer is only a DELAY impact")
    void delayNotThreateningConnection() {
        Station s1 = newStation("NA");
        Station s2 = newStation("NB");
        Station s3 = newStation("NC");
        long trainA = newTrain("810005", s1, s2);
        addStop(trainA, s1, 1, null, "08:00:00", 0);
        addStop(trainA, s2, 2, "10:00:00", "10:10:00", 0);
        long trainB = newTrain("810006", s2, s3);
        addStop(trainB, s2, 1, null, "12:00:00", 0);
        addStop(trainB, s3, 2, "14:00:00", null, 0);

        long journeyId = createJourney(
                new LegSpec(trainA, s1.getId(), s2.getId()),
                new LegSpec(trainB, s2.getId(), s3.getId()));

        setStatus(trainA, TrainRunningStatus.DELAYED, 45, "Late running");

        impactAnalysisAndMonitor();

        List<JourneyImpact> impacts = impactsOf(journeyId);
        assertThat(impacts).hasSize(1);
        JourneyImpact impact = impacts.get(0);
        assertThat(impact.getImpactType()).isEqualTo(ImpactType.DELAY);
        assertThat(impact.getSeverity()).isEqualTo(ImpactSeverity.LOW);
        assertThat(impact.getExplanation()).contains("not threatened");
    }

    // 5. Midnight crossing
    @Test
    @DisplayName("a connection that crosses midnight is evaluated with the next-day departure time")
    void midnightCrossingConnection() {
        Station s1 = newStation("XA");
        Station s2 = newStation("XB");
        Station s3 = newStation("XC");
        long trainA = newTrain("810007", s1, s2);
        addStop(trainA, s1, 1, null, "22:00:00", 0);
        addStop(trainA, s2, 2, "23:30:00", "23:35:00", 0);
        long trainB = newTrain("810008", s2, s3);
        // departs after midnight: one day later than leg 1's arrival
        addStop(trainB, s2, 1, null, "00:30:00", 1);
        addStop(trainB, s3, 2, "02:00:00", null, 1);

        long journeyId = createJourney(
                new LegSpec(trainA, s1.getId(), s2.getId()),
                new LegSpec(trainB, s2.getId(), s3.getId()));

        setStatus(trainA, TrainRunningStatus.DELAYED, 90, "Late running overnight");

        impactAnalysisAndMonitor();

        // Buffer is 00:30 - 23:30 = 60 min; a 90 min delay overshoots by 30 -> HIGH.
        JourneyImpact missed = impactOfType(impactsOf(journeyId), ImpactType.MISSED_CONNECTION);
        assertThat(missed.getSeverity()).isEqualTo(ImpactSeverity.HIGH);
        assertThat(missed.getDelayMinutes()).isEqualTo(90);
        assertThat(missed.getExplanation()).contains("buffer 60 min");
    }

    // 6. Unrelated journeys stay unaffected
    @Test
    @DisplayName("a journey whose trains have no disruption gets no impact")
    void unrelatedJourneyUnaffected() throws Exception {
        Station s1 = newStation("UA");
        Station s2 = newStation("UB");
        long disruptedTrain = newTrain("810009", s1, s2);
        addStop(disruptedTrain, s1, 1, null, "08:00:00", 0);
        addStop(disruptedTrain, s2, 2, "10:00:00", null, 0);
        long affectedJourney = createJourney(new LegSpec(disruptedTrain, s1.getId(), s2.getId()));

        Station s3 = newStation("UC");
        Station s4 = newStation("UD");
        long otherTrain = newTrain("810010", s3, s4);
        addStop(otherTrain, s3, 1, null, "08:00:00", 0);
        addStop(otherTrain, s4, 2, "10:00:00", null, 0);
        long otherJourney = createJourney(new LegSpec(otherTrain, s3.getId(), s4.getId()));

        setStatus(disruptedTrain, TrainRunningStatus.CANCELLED, 0, "Cancelled");

        impactAnalysisAndMonitor();

        assertThat(impactsOf(affectedJourney)).isNotEmpty();
        assertThat(impactsOf(otherJourney)).isEmpty();

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/impacts", otherJourney))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    // 7. Missing timetable information
    @Test
    @DisplayName("a missing scheduled arrival never produces a claimed missed connection")
    void missingScheduleInformationIsSafe() {
        Station s1 = newStation("PA");
        Station s2 = newStation("PB");
        Station s3 = newStation("PC");
        long trainA = newTrain("810011", s1, s2);
        addStop(trainA, s1, 1, null, "08:00:00", 0);
        // arrival time deliberately missing
        addStop(trainA, s2, 2, null, "10:00:00", 0);
        long trainB = newTrain("810012", s2, s3);
        addStop(trainB, s2, 1, null, "10:30:00", 0);
        addStop(trainB, s3, 2, "12:00:00", null, 0);

        long journeyId = createJourney(
                new LegSpec(trainA, s1.getId(), s2.getId()),
                new LegSpec(trainB, s2.getId(), s3.getId()));

        setStatus(trainA, TrainRunningStatus.DELAYED, 45, "Late running");

        impactAnalysisAndMonitor();

        List<JourneyImpact> impacts = impactsOf(journeyId);
        assertThat(typesOf(impacts)).containsExactly(ImpactType.DELAY);
        assertThat(impacts.get(0).getExplanation()).contains("cannot be computed");
    }

    // 8. Disruption on a different service date
    @Test
    @DisplayName("a disruption on another service date does not impact the journey")
    void disruptionOnDifferentServiceDate() {
        Station s1 = newStation("QA");
        Station s2 = newStation("QB");
        long trainId = newTrain("810013", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);
        long journeyId = createJourney(new LegSpec(trainId, s1.getId(), s2.getId()));

        // disruption is recorded for a different service date
        trainStatusService.upsert(trainId, new TrainStatusRequest(
                journeyDate.plusDays(1), TrainRunningStatus.CANCELLED, 0, null, "Other date", null, null));

        impactAnalysisAndMonitor();

        assertThat(impactsOf(journeyId)).isEmpty();
    }

    // 9. Resolved disruption does not create new active impacts
    @Test
    @DisplayName("resolving the disruption resolves the impacts on the next analysis run")
    void resolvedDisruptionReconcilesImpacts() {
        Station s1 = newStation("RA");
        Station s2 = newStation("RB");
        long trainId = newTrain("810014", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);
        long journeyId = createJourney(new LegSpec(trainId, s1.getId(), s2.getId()));

        setStatus(trainId, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        impactAnalysisAndMonitor();
        assertThat(activeImpactsOf(journeyId)).hasSize(2);

        // The train runs again: Phase 4 resolves the event, Phase 5 must follow.
        setStatus(trainId, TrainRunningStatus.ON_TIME, 0, null);
        ImpactAnalysisResult second = impactAnalysisAndMonitor();

        assertThat(second.impactsResolved()).isGreaterThanOrEqualTo(2);
        assertThat(activeImpactsOf(journeyId)).isEmpty();
        assertThat(impactsOf(journeyId))
                .allMatch(i -> i.getImpactState() == ImpactState.RESOLVED)
                .allMatch(i -> i.getResolvedAt() != null)
                .allMatch(i -> i.getActiveKey() == null);
    }

    // 10. Repeated analysis is idempotent
    @Test
    @DisplayName("running the analysis twice does not duplicate impacts")
    void repeatedAnalysisDoesNotDuplicate() throws Exception {
        Station s1 = newStation("IA");
        Station s2 = newStation("IB");
        long trainId = newTrain("810015", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);
        long journeyId = createJourney(new LegSpec(trainId, s1.getId(), s2.getId()));

        setStatus(trainId, TrainRunningStatus.CANCELLED, 0, "Cancelled");

        impactAnalysisAndMonitor();
        ImpactAnalysisResult second = impactAnalysisAndMonitor();

        assertThat(second.impactsCreated()).isZero();
        assertThat(second.impactsUnchanged()).isGreaterThanOrEqualTo(2);
        assertThat(impactsOf(journeyId)).hasSize(2);

        // The HTTP trigger is idempotent as well.
        mockMvc.perform(post("/api/v1/impact-analysis/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.impactsCreated").value(0))
                .andExpect(jsonPath("$.failures").value(0));
    }

    // 11. Filtering and pagination
    @Test
    @DisplayName("GET /api/v1/impacts filters and paginates deterministically")
    void filteringAndPagination() throws Exception {
        Station s1 = newStation("FA");
        Station s2 = newStation("FB");
        long cancelledTrain = newTrain("810016", s1, s2);
        addStop(cancelledTrain, s1, 1, null, "08:00:00", 0);
        addStop(cancelledTrain, s2, 2, "10:00:00", null, 0);
        long cancelledJourney = createJourney(new LegSpec(cancelledTrain, s1.getId(), s2.getId()));

        Station s3 = newStation("FC");
        Station s4 = newStation("FD");
        long delayedTrain = newTrain("810017", s3, s4);
        addStop(delayedTrain, s3, 1, null, "08:00:00", 0);
        addStop(delayedTrain, s4, 2, "10:00:00", null, 0);
        long delayedJourney = createJourney(new LegSpec(delayedTrain, s3.getId(), s4.getId()));

        setStatus(cancelledTrain, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        setStatus(delayedTrain, TrainRunningStatus.DELAYED, 150, "Very late");

        impactAnalysisAndMonitor();

        mockMvc.perform(get("/api/v1/impacts").param("journeyId", String.valueOf(cancelledJourney)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get("/api/v1/impacts")
                        .param("impactType", "DELAY")
                        .param("severity", "HIGH")
                        .param("minDelayMinutes", "120"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].journeyId").value(delayedJourney))
                .andExpect(jsonPath("$.content[0].delayMinutes").value(150));

        mockMvc.perform(get("/api/v1/impacts")
                        .param("impactState", "ACTIVE")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.size").value(1));

        mockMvc.perform(get("/api/v1/impacts").param("minDelayMinutes", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/impacts").param("impactType", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
    }

    // 12. Not found
    @Test
    @DisplayName("missing impact and journey ids return the standard 404 response")
    void missingIdsReturn404() throws Exception {
        mockMvc.perform(get("/api/v1/impacts/{id}", 999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/api/v1/journeys/{journeyId}/impacts", 999_999_999L))
                .andExpect(status().isNotFound());
    }

    // 13. Database-level duplicate prevention
    @Test
    @DisplayName("the unique active_key constraint rejects a duplicate ongoing impact")
    void uniqueActiveKeyRejectsDuplicate() {
        Station s1 = newStation("ZA");
        Station s2 = newStation("ZB");
        long trainId = newTrain("810018", s1, s2);
        addStop(trainId, s1, 1, null, "08:00:00", 0);
        addStop(trainId, s2, 2, "10:00:00", null, 0);
        long journeyId = createJourney(new LegSpec(trainId, s1.getId(), s2.getId()));

        setStatus(trainId, TrainRunningStatus.CANCELLED, 0, "Cancelled");
        impactAnalysisAndMonitor();

        JourneyImpact existing = activeImpactsOf(journeyId).get(0);
        JourneyImpact duplicate = new JourneyImpact();
        duplicate.setJourney(existing.getJourney());
        duplicate.setJourneyLeg(existing.getJourneyLeg());
        duplicate.setDisruptionEvent(existing.getDisruptionEvent());
        duplicate.setImpactType(existing.getImpactType());
        duplicate.setSeverity(existing.getSeverity());
        duplicate.setExplanation("duplicate");
        duplicate.setDetectedAt(OffsetDateTime.now());
        duplicate.setLastObservedAt(OffsetDateTime.now());
        duplicate.setImpactState(ImpactState.ACTIVE);
        duplicate.setActiveKey(existing.getActiveKey());

        assertThatThrownBy(() -> journeyImpactRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // 14. Regression: earlier phases still respond
    @Test
    @DisplayName("Phase 0-4 endpoints still respond after the Phase 5 additions")
    void earlierPhaseEndpointsStillRespond() throws Exception {
        mockMvc.perform(get("/api/v1/stations")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/trains")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/journeys")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/disruptions")).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/impacts")).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Monitoring creates the disruption event, then the analysis consumes it. */
    private ImpactAnalysisResult impactAnalysisAndMonitor() {
        disruptionMonitoringService.runMonitoring();
        return impactAnalysisService.runAnalysis();
    }

    private void setStatus(long trainId, TrainRunningStatus status, Integer delayMinutes, String reason) {
        trainStatusService.upsert(trainId, new TrainStatusRequest(
                journeyDate, status, delayMinutes, null, reason, null, null));
    }

    private long createJourney(LegSpec... legs) {
        long passengerId = newPassenger();
        List<JourneyLegRequest> requests = new ArrayList<>();
        for (int i = 0; i < legs.length; i++) {
            LegSpec leg = legs[i];
            requests.add(new JourneyLegRequest(leg.trainId(), leg.sourceId(), leg.destinationId(), i + 1));
        }
        return journeyService.create(new JourneyRequest(passengerId, journeyDate, requests)).id();
    }

    private long newPassenger() {
        Passenger passenger = new Passenger();
        passenger.setFullName("Impact Tester " + SEQ.incrementAndGet());
        passenger.setEmail("impact-" + SEQ.get() + "-" + System.nanoTime() + "@example.com");
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
        train.setName("Impact Demo " + number);
        train.setSourceStation(source);
        train.setDestinationStation(destination);
        train.setActive(true);
        return trainRepository.save(train).getId();
    }

    private void addStop(long trainId, Station station, int sequence, String arrival, String departure, int dayOffset) {
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

    private List<JourneyImpact> impactsOf(long journeyId) {
        return journeyImpactRepository.findAll().stream()
                .filter(impact -> impact.getJourney().getId().equals(journeyId))
                .sorted(Comparator.comparing(JourneyImpact::getId))
                .toList();
    }

    private List<JourneyImpact> activeImpactsOf(long journeyId) {
        return journeyImpactRepository
                .findByJourneyIdAndImpactStateOrderByDetectedAtAscIdAsc(journeyId, ImpactState.ACTIVE);
    }

    private static List<ImpactType> typesOf(List<JourneyImpact> impacts) {
        return impacts.stream().map(JourneyImpact::getImpactType).toList();
    }

    private static JourneyImpact impactOfType(List<JourneyImpact> impacts, ImpactType type) {
        return impacts.stream().filter(impact -> impact.getImpactType() == type).findFirst()
                .orElseThrow(() -> new AssertionError("No " + type + " impact found in " + typesOf(impacts)));
    }

    private record LegSpec(long trainId, long sourceId, long destinationId) {
    }
}
